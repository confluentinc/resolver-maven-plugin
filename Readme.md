Resolver Maven Plugin
=====================

A Maven plugin for resolving a version range of an artifact to the highest matching version.

For the version range format, see [GenericVersionScheme](https://github.com/eclipse/aether-core/blob/master/aether-util/src/main/java/org/eclipse/aether/util/version/GenericVersionScheme.java) from the Aether project.

Command-line example
------------------------

When using `-Dresolve.print -q`, the only output to the console will be the matching version number. This can be used for scripting.

    mvn com.subshell.maven:resolver-maven-plugin:resolve-range \
        -Dresolve.groupId=org.apache.maven -Dresolve.artifactId=maven-model \
        "-Dresolve.versionRange=[3.1.0, 3.3.max]" \
        -Dresolve.print -q

pom.xml example
---------------

This example writes the matching version number into the property `latestMavenModelVersion`.

    <plugin>
        <groupId>com.subshell.maven</groupId>
        <artifactId>resolver-maven-plugin</artifactId>
        <configuration>
            <groupId>org.apache.maven</groupId>
            <artifactId>maven-model</artifactId>
            <version>[3.1.0, 3.3.max]</version>
            <property>latestMavenModelVersion</property>
        </configuration>
        <executions>
            <execution>
                <goals>
                    <goal>resolve-range</goal>
                </goals>
            </execution>
        </executions>
    </plugin>

System requirements
-------------------

* Maven 3.2.5
* Java 7
