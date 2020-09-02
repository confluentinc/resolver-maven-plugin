#!/usr/bin/env groovy

// Copyright 2020 Confluent Inc

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '')),
        parameters([
            string(name: 'RELEASE_TAG', defaultValue: '')
        ])
])

// We use the oldest available jdk version.
node('docker-oraclejdk8') {
  stage('Source') {
    checkout([
        $class: 'GitSCM',
        branches: scm.branches,
        doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
        extensions: [[$class: 'CloneOption', noTags: false, shallow: false, depth: 0, reference: '']],
        userRemoteConfigs: scm.userRemoteConfigs,
    ])

    // If we have a RELEASE_TAG specified as a build parameter, test that the version in pom.xml matches the tag.
    if ( !params.RELEASE_TAG.trim().equals('') ) {
        sh "git checkout ${params.RELEASE_TAG}"
        def project_version = sh (
                        script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout | tail -1',
                        returnStdout: true
                        ).trim()

        if ( !params.RELEASE_TAG.trim().equals("v" + project_version.trim()) ){
            echo 'ERROR: tag doesn\'t match project version, please correct and try again'
            echo "Tag: ${params.RELEASE_TAG}"
            echo "Project version: ${project_version}"
            currentBuild.result = 'FAILURE'
            return
        }
    }
  }
  stage('Build') {
      archiveArtifacts artifacts: 'pom.xml'
      withVaultEnv([["artifactory/tools_jenkins", "user", "TOOLS_ARTIFACTORY_USER"],
          ["artifactory/tools_jenkins", "password", "TOOLS_ARTIFACTORY_PASSWORD"],
          ["sonatype/confluent", "user", "SONATYPE_OSSRH_USER"],
          ["sonatype/confluent", "password", "SONATYPE_OSSRH_PASSWORD"],
          ["gpg/packaging", "passphrase", "GPG_PASSPHRASE"]]) {
          withVaultFile([["maven/jenkins_maven_global_settings", "settings_xml", "maven-global-settings.xml", "MAVEN_GLOBAL_SETTINGS_FILE"],
              ["gpg/packaging", "private_key", "confluent-packaging-private.key", "GPG_PRIVATE_KEY"]]) {
              withMaven(globalMavenSettingsFilePath: "${env.MAVEN_GLOBAL_SETTINGS_FILE}") {

                  if ( params.RELEASE_TAG.trim().equals('') ) {
                      sh "mvn --batch-mode -Pjenkins clean verify install dependency:analyze site validate -U"
                  } else {
                      // it's a parameterized job, and we should deploy to maven central.
                      sh '''
                          gpg --import < $GPG_PRIVATE_KEY;
                          mvn --batch-mode clean deploy -P maven-central -Dgpg.passphrase=$GPG_PASSPHRASE
                      '''
                  }
                  currentBuild.result = 'Success'
              }
          }
      }
  }
}