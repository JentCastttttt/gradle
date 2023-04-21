/*
 * Copyright 2016 the original author or authors.
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

/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MavenDependencyType
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata
import org.gradle.internal.component.model.ConfigurationGraphResolveState
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.ConfigurationNotFoundException
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.VariantArtifactGraphResolveMetadata
import org.gradle.internal.component.model.VariantGraphResolveState

class MavenDependencyDescriptorTest extends ExternalDependencyDescriptorTest {
    final ModuleExclusions moduleExclusions = new ModuleExclusions()
    final ExcludeSpec nothing = moduleExclusions.nothing()

    @Override
    ExternalDependencyDescriptor create(ModuleComponentSelector selector) {
        return mavenDependencyMetadata(MavenScope.Compile, selector, [])
    }

    ExternalDependencyDescriptor createWithExcludes(ModuleComponentSelector selector, List<Exclude> excludes) {
        return mavenDependencyMetadata(MavenScope.Compile, selector, excludes)
    }

    def "excludes nothing when no exclude rules provided"() {
        def dep = createWithExcludes(requested, [])

        expect:
        def exclusions = moduleExclusions.excludeAny(dep.allExcludes)
        exclusions == nothing
        exclusions.is(moduleExclusions.excludeAny(dep.allExcludes))
    }

    def "applies exclude rules when traversing a configuration"() {
        def exclude1 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def exclude2 = new DefaultExclude(DefaultModuleIdentifier.newId("group2", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude1, exclude2])

        expect:
        def exclusions = moduleExclusions.excludeAny(dep.allExcludes)
        exclusions == moduleExclusions.excludeAny(ImmutableList.of(exclude1, exclude2))
        exclusions.is(moduleExclusions.excludeAny(dep.allExcludes))
    }

    def "selects compile and master configurations from target when traversing from compile configuration"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toState = Stub(ComponentGraphResolveState)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        def fromCompile = Stub(ModuleConfigurationMetadata)
        def toCompile = configuration(toState, "compile")
        def toMaster = configurationWithArtifacts(toState, "master")
        fromCompile.name >> "compile"
        toState.metadata >> toComponent

        def dep = mavenDependencyMetadata(MavenScope.Compile, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromCompile, toState).variants == [toCompile, toMaster]
    }

    def "selects compile, runtime and master configurations from target when traversing from other configuration"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toState = Stub(ComponentGraphResolveState)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        def fromRuntime = Stub(ModuleConfigurationMetadata)
        def fromRuntime2 = Stub(ModuleConfigurationMetadata)
        def toRuntime = configuration(toState, "runtime")
        def toCompile = configuration(toState, "compile")
        def toMaster = configurationWithArtifacts(toState, "master")
        fromRuntime.name >> "runtime"
        fromRuntime2.name >> "provided"
        toState.metadata >> toComponent

        def dep = mavenDependencyMetadata(MavenScope.Compile, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toState).variants == [toRuntime, toCompile, toMaster]
        dep.selectLegacyConfigurations(fromComponent, fromRuntime2, toState).variants == [toRuntime, toCompile, toMaster]
    }

    def "selects runtime and master configurations from target when traversing from other configuration and target's runtime extends compile"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toState = Stub(ComponentGraphResolveState)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        def fromRuntime = Stub(ModuleConfigurationMetadata)
        def fromRuntime2 = Stub(ModuleConfigurationMetadata)
        def toRuntime = configurationWithHierarchy(toState, "runtime", ImmutableSet.of("runtime", "compile"))
        def toMaster = configurationWithArtifacts(toState, "master")
        fromRuntime.name >> "runtime"
        fromRuntime2.name >> "provided"
        toState.metadata >> toComponent

        def dep = mavenDependencyMetadata(MavenScope.Compile, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toState).variants == [toRuntime, toMaster]
        dep.selectLegacyConfigurations(fromComponent, fromRuntime2, toState).variants == [toRuntime, toMaster]
    }

    def "ignores missing master configuration"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toState = Stub(ComponentGraphResolveState)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        def fromRuntime = Stub(ModuleConfigurationMetadata)
        def toRuntime = configurationWithHierarchy(toState, "runtime", ImmutableSet.of("compile", "runtime"))
        fromRuntime.name >> "runtime"
        toState.metadata >> toComponent

        def dep = mavenDependencyMetadata(MavenScope.Compile, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toState).variants == [toRuntime]
    }

    def "ignores empty master configuration"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toState = Stub(ComponentGraphResolveState)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        def fromRuntime = Stub(ModuleConfigurationMetadata)
        def toRuntime = configurationWithHierarchy(toState, "runtime", ImmutableSet.of("compile", "runtime"))
        def toMaster = configuration(toState, "master")
        fromRuntime.name >> "runtime"
        toState.metadata >> toComponent

        def dep = mavenDependencyMetadata(MavenScope.Compile, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toState).variants == [toRuntime]
    }

    def "falls back to default configuration when compile is not defined in target component"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toState = Stub(ComponentGraphResolveState)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        def fromCompile = Stub(ModuleConfigurationMetadata)
        def toDefault = configuration(toState, "default")
        def toMaster = configurationWithArtifacts(toState, "master")
        fromCompile.name >> "compile"
        toState.metadata >> toComponent
        toState.getConfiguration(_) >> null

        def dep = mavenDependencyMetadata(MavenScope.Compile, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromCompile, toState).variants == [toDefault, toMaster]
    }

    def "falls back to default configuration when runtime is not defined in target component"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toState = Stub(ComponentGraphResolveState)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        def fromRuntime = Stub(ModuleConfigurationMetadata)
        def toDefault = configurationWithHierarchy(toState, "default", ImmutableSet.of("compile", "default"))
        def toMaster = configurationWithArtifacts(toState, "master")
        fromRuntime.name >> "runtime"
        toState.metadata >> toComponent
        toState.getConfiguration(_) >> null

        def dep = mavenDependencyMetadata(MavenScope.Compile, Stub(ModuleComponentSelector), [])

        expect:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toState).variants == [toDefault, toMaster]
    }

    def "fails when compile configuration is not defined in target component"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toState = Stub(ComponentGraphResolveState)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        def fromCompile = Stub(ConfigurationMetadata)
        fromCompile.name >> "compile"
        toState.metadata >> toComponent
        toState.getConfiguration(_) >> null

        def dep = mavenDependencyMetadata(MavenScope.Compile, Stub(ModuleComponentSelector), [])

        when:
        dep.selectLegacyConfigurations(fromComponent, fromCompile, toState)

        then:
        thrown(ConfigurationNotFoundException)
    }

    def "fails when runtime configuration is not defined in target component"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toState = Stub(ComponentGraphResolveState)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        def fromRuntime = Stub(ConfigurationMetadata)
        fromRuntime.name >> "runtime"
        toState.metadata >> toComponent
        toState.getConfiguration(_) >> null

        def dep = mavenDependencyMetadata(MavenScope.Compile, Stub(ModuleComponentSelector), [])

        when:
        dep.selectLegacyConfigurations(fromComponent, fromRuntime, toState)

        then:
        thrown(ConfigurationNotFoundException)
    }

    private static MavenDependencyDescriptor mavenDependencyMetadata(MavenScope scope, ModuleComponentSelector selector, List<ExcludeMetadata> excludes) {
        return new MavenDependencyDescriptor(scope, MavenDependencyType.DEPENDENCY, selector, null, excludes)
    }
}
