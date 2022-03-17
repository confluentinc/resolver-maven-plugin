#!/usr/bin/env groovy

// Copyright 2020 Confluent Inc

def RelaseTag = string(name: 'RELEASE_TAG', defaultValue: '',
                                description: 'Provide the tag of plugin that will be release to maven central,' +
                                'only use the value when you want to release to maven central')

def config = jobConfig {
    owner = 'tools'
    slackChannel = 'devprod-notifications'
    nodeLabel = 'docker-debian-jdk8'
    usesDockerForTesting = false
    runMergeCheck = false
    testResultSpecs = ['junit': 'test/results.xml']
    properties = [parameters([RelaseTag])]
    cron = '' //disable cron builds
}

def job = {
    // If we have a RELEASE_TAG specified as a build parameter, test that the version in pom.xml matches the tag.
    if ( !params.RELEASE_TAG.trim().equals('') ) {
        def project_version = sh (
                        script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout | tail -1',
                        returnStdout: true
                        ).trim()

        if ( !params.RELEASE_TAG.trim().equals(project_version) ){
            echo 'ERROR: tag doesn\'t match project version, please correct and try again'
            echo "Tag: ${params.RELEASE_TAG}"
            echo "Project version: ${project_version}"
            currentBuild.result = 'FAILURE'
            return
        }
    }

  stage('Build') {
      archiveArtifacts artifacts: 'pom.xml'
      def mavenSettingsFile = "${env.WORKSPACE_TMP}/maven-global-settings.xml"
      withVaultEnv([["gpg/packaging", "passphrase", "GPG_PASSPHRASE"]]) {
          withVaultFile([["gpg/packaging", "private_key", "confluent-packaging-private.key", "GPG_PRIVATE_KEY"]]) {
              withMavenSettings("maven/jenkins_maven_global_settings", "settings", "MAVEN_GLOBAL_SETTINGS", mavenSettingsFile) {
                  withMaven(globalMavenSettingsFilePath: mavenSettingsFile) {

                      if ( params.RELEASE_TAG.trim().equals('') ) {
                          sh "mvn --batch-mode -Pjenkins clean verify install dependency:analyze site validate -U"
                      } else {
                          // it's a parameterized job, and we should deploy to maven central.
                          sh '''
                              set +x
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
}
runJob config, job
