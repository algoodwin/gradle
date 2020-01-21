/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Issue

class IvyPublishMultiProjectIntegTest extends AbstractIvyPublishIntegTest {
    def project1 = javaLibrary(ivyRepo.module("org.gradle.test", "project1", "1.0"))
    def project2 = javaLibrary(ivyRepo.module("org.gradle.test", "project2", "2.0"))
    def project3 = javaLibrary(ivyRepo.module("org.gradle.test", "project3", "3.0"))

    @ToBeFixedForInstantExecution
    def "project dependencies are correctly bound to published project"() {
        createBuildScripts("")

        when:
        run "publish"

        then:
        project1.assertPublishedAsJavaModule()
        project1.assertApiDependencies("org.gradle.test:project2:2.0", "org.gradle.test:project3:3.0")

        project2.assertPublishedAsJavaModule()
        project2.assertApiDependencies("org.gradle.test:project3:3.0")

        project3.assertPublishedAsJavaModule()
        project3.assertApiDependencies()

        and:
        resolveArtifacts(project1) { expectFiles 'project1-1.0.jar', 'project2-2.0.jar', 'project3-3.0.jar' }
    }

    @ToBeFixedForInstantExecution
    def "project dependencies reference publication identity of dependent project"() {
        def project3 = javaLibrary(ivyRepo.module("changed.org", "changed-module", "changed"))

        createBuildScripts("""
project(":project3") {
    publishing {
        publications.ivy {
            organisation "changed.org"
            module "changed-module"
            revision "changed"
        }
    }
}
""")

        when:
        run "publish"

        then:
        project1.assertPublishedAsJavaModule()
        project1.assertApiDependencies("org.gradle.test:project2:2.0", "changed.org:changed-module:changed")

        project2.assertPublishedAsJavaModule()
        project2.assertApiDependencies("changed.org:changed-module:changed")

        project3.assertPublishedAsJavaModule()
        project3.assertApiDependencies()

        and:
        resolveArtifacts(project1) { expectFiles 'changed-module-changed.jar', 'project1-1.0.jar', 'project2-2.0.jar' }
    }

    def "reports failure when project dependency references a project with multiple conflicting publications"() {
        createBuildScripts("""
project(":project3") {
    publishing {
        publications {
            extraComponent(IvyPublication) {
                from components.java
                organisation "extra.org"
                module "extra-module"
                revision "extra"
            }
            extra(IvyPublication) {
                organisation "extra.org"
                module "extra-module-2"
                revision "extra"
            }
        }
    }
}
""")

        when:
        fails "publish"

        then:
        failure.assertHasCause """Publishing is not able to resolve a dependency on a project with multiple publications that have different coordinates.
Found the following publications in project ':project3':
  - Ivy publication 'ivy' with coordinates org.gradle.test:project3:3.0
  - Ivy publication 'extraComponent' with coordinates extra.org:extra-module:extra
  - Ivy publication 'extra' with coordinates extra.org:extra-module-2:extra"""
    }

    @ToBeFixedForInstantExecution
    def "referenced project can have additional non-component publications"() {
        createBuildScripts("""
project(":project3") {
    publishing {
        publications {
            extra(IvyPublication) {
                organisation "extra.org"
                module "extra-module-2"
                revision "extra"
            }
        }
    }
}
""")

        expect:
        succeeds "publish"
    }

    @ToBeFixedForInstantExecution
    def "referenced project can have multiple additional publications that contain a child of some other publication"() {
        createBuildScripts("""
// TODO - replace this with a public API when available
class ExtraComp implements org.gradle.api.internal.component.SoftwareComponentInternal, ComponentWithVariants {
    String name = 'extra'
    Set usages = []
    Set variants = []
}

project(":project3") {
    def e1 = new ExtraComp(variants: [components.java])
    def e2 = new ExtraComp(variants: [e1, components.java])

    publishing {
        publications {
            extra1(IvyPublication) {
                from e1
                organisation "extra.org"
                module "extra-1"
                revision "extra"
            }
            extra2(IvyPublication) {
                from e2
                organisation "custom"
                module "custom3"
                revision "456"
            }
        }
    }
}
""")

        when:
        succeeds "publish"

        then:
        project1.assertApiDependencies("org.gradle.test:project2:2.0", "custom:custom3:456")
    }

    @ToBeFixedForInstantExecution
    def "ivy-publish plugin does not take archivesBaseName into account"() {
        createBuildScripts("""
project(":project2") {
    archivesBaseName = "changed"
}
        """)

        when:
        run "publish"

        then:
        project1.assertPublishedAsJavaModule()
        project1.assertApiDependencies("org.gradle.test:project2:2.0", "org.gradle.test:project3:3.0")

        // published with the correct coordinates, even though artifact has different name
        project2.assertPublishedAsJavaModule()
        project2.assertApiDependencies("org.gradle.test:project3:3.0")

        project3.assertPublishedAsJavaModule()
        project3.parsedIvy.dependencies.isEmpty()
    }

    @ToBeFixedForInstantExecution
    def "ivy-publish plugin uses target project name for project dependency when target project does not have ivy-publish plugin applied"() {
        given:
        settingsFile << """
include "project1", "project2"
        """

        buildFile << """
allprojects {
    group = "org.gradle.test"
    version = "1.0"
}

project(":project1") {
    apply plugin: "java-library"
    apply plugin: "ivy-publish"

    dependencies {
        api project(":project2")
    }

    publishing {
        repositories {
            ivy { url "${ivyRepo.uri}" }
        }
        publications {
            ivy(IvyPublication) {
                from components.java
            }
        }
    }
}
project(":project2") {
    apply plugin: 'java'
    archivesBaseName = "changed"
}
        """

        when:
        run "publish"

        then:
        project1.assertPublishedAsJavaModule()
        project1.assertApiDependencies("org.gradle.test:project2:1.0")
    }

    @ToBeFixedForInstantExecution
    def "ivy-publish plugin publishes project dependency excludes in descriptor"() {
        given:
        settingsFile << """
include 'project1', 'project2'
"""

        buildFile << """
allprojects {
    group = 'org.gradle.test'
    version = '1.0'
}

project(':project1') {
    apply plugin: 'java'

    ${jcenterRepository()}

    dependencies {
        implementation 'commons-logging:commons-logging:1.2'
    }
}

project(':project2') {
    apply plugin: "java"
    apply plugin: "ivy-publish"

    version = '2.0'

    dependencies {
        implementation project(":project1"), {
            exclude group: 'commons-logging', module: 'commons-logging'
        }
    }

    publishing {
        repositories {
            ivy { url "${ivyRepo.uri}" }
        }
        publications {
            ivy(IvyPublication) {
                from components.java
            }
        }
    }
}
"""

        when:
        run "publish"

        then:
        project2.assertPublishedAsJavaModule()
        def dependency = project2.parsedIvy.expectDependency("org.gradle.test:project1:1.0")
        dependency.exclusions.size() == 1
        dependency.exclusions[0].org == 'commons-logging'
        dependency.exclusions[0].module == 'commons-logging'
    }

    private void createBuildScripts(String append = "") {
        settingsFile << """
include "project1", "project2", "project3"
        """

        buildFile << """
allprojects {
    group = "org.gradle.test"
    version = "3.0"
}

subprojects {
    apply plugin: "java-library"
    apply plugin: "ivy-publish"

    publishing {
        repositories {
            ivy { url "${ivyRepo.uri}" }
        }
        publications {
            ivy(IvyPublication) {
                from components.java
            }
        }
    }
}

project(":project1") {
    version = "1.0"
    dependencies {
        api project(":project2")
        api project(":project3")
    }
}
project(":project2") {
    version = "2.0"
    dependencies {
        api project(":project3")
    }
}

$append
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/847")
    def "fails publishing projects which share the same GAV coordinates"() {
        given:
        settingsFile << """
            rootProject.name='duplicates'
            include 'a:core'
            include 'b:core'
        """

        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                apply plugin: 'ivy-publish'
                group 'org.gradle.test'
                version '1.0'

                publishing {
                    repositories {
                        ivy { url "${ivyRepo.uri}" }
                    }
                    publications {
                        ivy(IvyPublication) {
                            from components.java
                        }
                    }
                }
            }

            project(':a:core') {
                dependencies {
                    implementation project(':b:core')
                }
            }
        """

        when:
        fails 'publishIvyPublicationToIvyRepo'

        then:
        failure.assertHasCause "Project :b:core has the same (organisation, module name) as :a:core. You should set both the organisation and module name of the publication or opt out by adding the org.gradle.dependency.duplicate.project.detection system property to 'false'."
    }

    @ToBeFixedForInstantExecution
    @Issue("https://github.com/gradle/gradle/issues/847")
    def "fails publishing projects if they share the same GAV coordinates unless detection is disabled"() {
        given:
        settingsFile << """
            rootProject.name='duplicates'
            include 'a:core'
            include 'b:core'
        """

        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                apply plugin: 'ivy-publish'
                group 'org.gradle.test'
                version '1.0'

                publishing {
                    repositories {
                        ivy { url "${ivyRepo.uri}" }
                    }
                    publications {
                        ivy(IvyPublication) {
                            from components.java
                        }
                    }
                }
            }

        """

        when:
        succeeds 'publishIvyPublicationToIvyRepo', '-Dorg.gradle.dependency.duplicate.project.detection=false'
        def project1 = javaLibrary(ivyRepo.module("org.gradle.test", "core", "1.0"))

        then:
        // this tests the current behavior, which is obviously wrong here as the user
        // didn't overwrite the publication coordinates
        project1.assertPublishedAsJavaModule()
        project1.parsedIvy.module == 'core'
    }

    @ToBeFixedForInstantExecution
    @Issue("https://github.com/gradle/gradle/issues/847")
    def "can avoid publishing warning with projects with the same name by setting an explicit artifact id"() {
        given:
        settingsFile << """
            rootProject.name='duplicates'
            include 'a:core'
            include 'b:core'
        """

        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                apply plugin: 'ivy-publish'
                group 'org.gradle.test'
                version '1.0'

                publishing {
                    repositories {
                        ivy { url "${ivyRepo.uri}" }
                    }
                }
            }

            project(':a:core') {
                dependencies {
                    implementation project(':b:core')
                }
                publishing.publications {
                     ivy(IvyPublication) {
                        organisation = 'org.gradle.test'
                        module = 'some-a'
                        from components.java
                    }
                }
            }

            project(':b:core') {
                publishing.publications {
                     ivy(IvyPublication) {
                        organisation = 'org.gradle.test'
                        module = 'some-b'
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'publishIvyPublicationToIvyRepo'
        def project1 = javaLibrary(ivyRepo.module("org.gradle.test", "some-a", "1.0"))
        def project2 = javaLibrary(ivyRepo.module("org.gradle.test", "some-b", "1.0"))

        then:
        project1.assertPublishedAsJavaModule()
        project2.assertPublishedAsJavaModule()

        project1.parsedIvy.module == 'some-a'
        project2.parsedIvy.module == 'some-b'

        project1.parsedIvy.assertConfigurationDependsOn("runtime", "org.gradle.test:some-b:1.0")

        project1.parsedModuleMetadata.component.module == 'some-a'
        project2.parsedModuleMetadata.component.module == 'some-b'

        project1.parsedModuleMetadata.variant("runtimeElements") {
            dependency("org.gradle.test", "some-b", "1.0")
            noMoreDependencies()
        }

        and:
        outputDoesNotContain "Project :a:core has the same (organisation, module name) as :b:core.  You should set both the organisation and module name of the publication or opt out by adding the org.gradle.dependency.duplicate.project.detection system property to 'false'."
        outputDoesNotContain "Project :b:core has the same (organisation, module name) as :a:core.  You should set both the organisation and module name of the publication or opt out by adding the org.gradle.dependency.duplicate.project.detection system property to 'false'."
    }

    @ToBeFixedForInstantExecution
    @Issue("https://github.com/gradle/gradle/issues/847")
    def "can avoid publishing warning with projects with the same name by setting an explicit group id"() {
        given:
        settingsFile << """
            rootProject.name='duplicates'
            include 'a:core'
            include 'b:core'
        """

        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                apply plugin: 'ivy-publish'
                group 'org.gradle.test'
                version '1.0'

                publishing {
                    repositories {
                        ivy { url "${ivyRepo.uri}" }
                    }
                }
            }

            project(':a:core') {
                dependencies {
                    implementation project(':b:core')
                }
                publishing.publications {
                     ivy(IvyPublication) {
                        organisation = 'org.gradle.test2'
                        module = 'core'
                        from components.java
                    }
                }
            }

            project(':b:core') {
                publishing.publications {
                     ivy(IvyPublication) {
                        organisation = 'org.gradle.test'
                        module = 'core'
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds 'publishIvyPublicationToIvyRepo'
        def project1 = javaLibrary(ivyRepo.module("org.gradle.test2", "core", "1.0"))
        def project2 = javaLibrary(ivyRepo.module("org.gradle.test", "core", "1.0"))

        then:
        project1.assertPublishedAsJavaModule()
        project2.assertPublishedAsJavaModule()

        project1.parsedIvy.organisation == 'org.gradle.test2'
        project2.parsedIvy.organisation == 'org.gradle.test'

        project1.parsedIvy.assertConfigurationDependsOn("runtime", "org.gradle.test:core:1.0")

        project1.parsedModuleMetadata.component.group == 'org.gradle.test2'
        project2.parsedModuleMetadata.component.group == 'org.gradle.test'

        project1.parsedModuleMetadata.variant("runtimeElements") {
            dependency("org.gradle.test", "core", "1.0")
            noMoreDependencies()
        }

        and:
        outputDoesNotContain "Project :a:core has the same (organisation, module name) as :b:core. You should set both the organisation and module name of the publication or opt out by adding the org.gradle.dependency.duplicate.project.detection system property to 'false'."
        outputDoesNotContain "Project :b:core has the same (organisation, module name) as :a:core. You should set both the organisation and module name of the publication or opt out by adding the org.gradle.dependency.duplicate.project.detection system property to 'false'."

    }

}
