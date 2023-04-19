/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

/**
 * Smoke test verifying the external plugins.
 *
 */
@Requires(value = TestPrecondition.JDK9_OR_LATER, adhoc = {
    GradleContextualExecuter.isNotConfigCache() && GradleBuildJvmSpec.isAvailable()
})
class GradleBuildExternalPluginsValidationSmokeTest extends AbstractGradleceptionSmokeTest implements WithPluginValidation, ValidationMessageChecker {

    def setup() {
        ProjectBuildDirLocator locator = (String projectPath, TestFile projectRoot) -> {
            if (projectPath == ':') {
                projectRoot.file("build")
            } else {
                def projectDir = projectPath.split(':').join('/')

                // Search in `subprojects` first.
                def subprojectsPath = projectRoot.file("subprojects").file(projectDir).file("build")

                if (subprojectsPath.exists()) {
                    return subprojectsPath
                }

                // Otherwise, it might exist in a platform.
                for (TestFile platformDir : projectRoot.file("platforms").listFiles()) {
                    def platformPath = platformDir.file(projectDir).file("build")
                    if (platformPath.exists()) {
                        return platformPath
                    }
                }

                throw new IllegalArgumentException("Cannot find build dir for project path '$projectPath'")
            }
        }

        // Cache this since this gets called multiple times per project path.
        Map<String, TestFile> locations = new HashMap<>()
        allPlugins.projectPathToBuildDir = (String projectPath, TestFile projectRoot) -> {
            locations.computeIfAbsent(projectPath, path -> locator.getBuildDir(path, projectRoot))
        }
    }

    def "performs static validation of plugins used by the Gradle build"() {
        when:
        passingPlugins { id ->
            id.startsWith('gradlebuild') ||
            id.startsWith('Gradlebuild') ||
            id in [
                'com.diffplug.spotless',
                'org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin',
                'org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin',
                'me.champeau.jmh',
                'kotlin-sam-with-receiver',
                'org.jetbrains.gradle.plugin.idea-ext',
                'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin',
                'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingKotlinGradleSubplugin',
                'org.jetbrains.kotlin.jvm',
                'org.jlleitschuh.gradle.ktlint',
                'org.jlleitschuh.gradle.ktlint.KtlintBasePlugin',
                'org.jlleitschuh.gradle.ktlint.KtlintIdeaPlugin',
                'org.jetbrains.kotlin.gradle.targets.js.npm.NpmResolverPlugin',
                'org.jetbrains.kotlin.js',
                'org.asciidoctor.gradle.base.AsciidoctorBasePlugin',
                'org.asciidoctor.gradle.jvm.AsciidoctorJBasePlugin',
                'org.asciidoctor.gradle.jvm.AsciidoctorJPlugin',
                'org.asciidoctor.jvm.convert',
                'com.gradle.plugin-publish',
                'kotlin',
                'com.autonomousapps.dependency-analysis',
                'dev.adamko.dokkatoo.DokkatooBasePlugin$Inject',
                'dev.adamko.dokkatoo.adapters.DokkatooJavaAdapter$Inject',
                'dev.adamko.dokkatoo.adapters.DokkatooKotlinAdapter$Inject',
                'dev.adamko.dokkatoo.formats.DokkatooHtmlPlugin$Inject',
            ]
        }

        then:
        validatePlugins()
    }

    void passingPlugins(Closure<Boolean> spec) {
        allPlugins.passing(spec)
    }

    void validatePlugins() {
        allPlugins.performValidation([
            "--no-parallel" // make sure we have consistent execution ordering as we skip cached tasks
        ])
    }

    void inProject(String projectPath, @DelegatesTo(value = ProjectValidation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def validation = new ProjectValidation(projectPath)
        spec.delegate = validation
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    class ProjectValidation {
        private final String projectPath

        ProjectValidation(String projectPath) {
            this.projectPath = projectPath
        }

        void onPlugin(String id, @DelegatesTo(value = PluginValidation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            allPlugins.onPlugin(id, projectPath, spec)
        }
    }
}
