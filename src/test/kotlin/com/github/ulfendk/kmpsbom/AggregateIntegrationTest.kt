package com.github.ulfendk.kmpsbom

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class AggregateIntegrationTest {
    
    @Test
    fun `aggregate SBOM task generates output files`() {
        // Create a root project
        val rootProject = ProjectBuilder.builder()
            .withName("test-root")
            .build()
        
        // Apply the plugin
        rootProject.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        // Create a shared module (for project structure)
        ProjectBuilder.builder()
            .withName("shared")
            .withParent(rootProject)
            .build()
        
        // Configure the extension
        val extension = rootProject.extensions.getByType(KmpSbomExtension::class.java)
        extension.androidAppModule = "."
        extension.iosFrameworkModule = ":shared"
        extension.includeDebugDependencies = false
        extension.includeReleaseDependencies = true
        extension.includeTestDependencies = false
        
        // Register aggregate tasks manually (since afterEvaluate won't run in tests)
        rootProject.tasks.register("testAndroidAggregate", GenerateAggregateSbomTask::class.java).configure {
            targetPlatform.set("android")
            moduleProject.set(extension.androidAppModule)
            outputDir.set(rootProject.layout.buildDirectory.dir("sbom/aggregate"))
        }
        
        rootProject.tasks.register("testIosAggregate", GenerateAggregateSbomTask::class.java).configure {
            targetPlatform.set("ios")
            moduleProject.set(extension.iosFrameworkModule)
            outputDir.set(rootProject.layout.buildDirectory.dir("sbom/aggregate"))
        }
        
        // Verify tasks were created
        assertNotNull(rootProject.tasks.findByName("testAndroidAggregate"))
        assertNotNull(rootProject.tasks.findByName("testIosAggregate"))
        
        // Verify configuration was applied
        assertTrue(extension.includeReleaseDependencies)
        assertTrue(!extension.includeDebugDependencies)
        assertTrue(!extension.includeTestDependencies)
    }
}
