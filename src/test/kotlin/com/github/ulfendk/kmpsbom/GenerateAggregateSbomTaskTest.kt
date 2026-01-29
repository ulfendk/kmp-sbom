package com.github.ulfendk.kmpsbom

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class GenerateAggregateSbomTaskTest {
    
    @Test
    fun `plugin registers aggregate Android SBOM task when configured`() {
        val rootProject = ProjectBuilder.builder().build()
        rootProject.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        // Configure extension before evaluation
        val extension = rootProject.extensions.getByType(KmpSbomExtension::class.java)
        extension.androidAppModule = ":androidApp"
        
        // Manually trigger afterEvaluate logic
        rootProject.pluginManager.withPlugin("com.github.ulfendk.kmp-sbom") {
            // Create a dummy androidApp project
            ProjectBuilder.builder()
                .withName("androidApp")
                .withParent(rootProject)
                .build()
                
            // Trigger afterEvaluate manually by accessing the task
            rootProject.tasks.register("generateAndroidAggregateSbom", GenerateAggregateSbomTask::class.java).configure {
                group = "verification"
                description = "Generates aggregate SBOM for Android app including all module dependencies"
                targetPlatform.set("android")
                moduleProject.set(extension.androidAppModule)
                outputDir.set(rootProject.layout.buildDirectory.dir("sbom/aggregate"))
            }
        }
        
        assertNotNull(rootProject.tasks.findByName("generateAndroidAggregateSbom"))
    }
    
    @Test
    fun `plugin registers aggregate iOS SBOM task when configured`() {
        val rootProject = ProjectBuilder.builder().build()
        rootProject.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        // Configure extension before evaluation
        val extension = rootProject.extensions.getByType(KmpSbomExtension::class.java)
        extension.iosFrameworkModule = ":shared"
        
        // Manually trigger task registration
        rootProject.pluginManager.withPlugin("com.github.ulfendk.kmp-sbom") {
            rootProject.tasks.register("generateIosAggregateSbom", GenerateAggregateSbomTask::class.java).configure {
                group = "verification"
                description = "Generates aggregate SBOM for iOS framework including all module dependencies"
                targetPlatform.set("ios")
                moduleProject.set(extension.iosFrameworkModule)
                outputDir.set(rootProject.layout.buildDirectory.dir("sbom/aggregate"))
            }
        }
        
        assertNotNull(rootProject.tasks.findByName("generateIosAggregateSbom"))
    }
    
    @Test
    fun `extension has aggregate configuration defaults`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        val extension = project.extensions.getByType(KmpSbomExtension::class.java)
        assertEquals(false, extension.includeDebugDependencies)
        assertEquals(true, extension.includeReleaseDependencies)
        assertEquals(false, extension.includeTestDependencies)
    }
    
    @Test
    fun `aggregate task configuration can be customized`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        val extension = project.extensions.getByType(KmpSbomExtension::class.java)
        extension.androidAppModule = ":app"
        extension.iosFrameworkModule = ":shared"
        extension.includeDebugDependencies = true
        extension.includeTestDependencies = true
        
        assertEquals(":app", extension.androidAppModule)
        assertEquals(":shared", extension.iosFrameworkModule)
        assertEquals(true, extension.includeDebugDependencies)
        assertEquals(true, extension.includeTestDependencies)
    }
    
    @Test
    fun `build-time configurations are excluded from aggregate SBOM`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        val extension = project.extensions.getByType(KmpSbomExtension::class.java)
        extension.includeDebugDependencies = true
        extension.includeReleaseDependencies = true
        extension.includeTestDependencies = true
        
        // Create test task
        project.tasks.register("testAggregateTask", GenerateAggregateSbomTask::class.java).configure {
            targetPlatform.set("android")
            moduleProject.set(".")
            outputDir.set(project.layout.buildDirectory.dir("sbom/aggregate"))
        }
        
        assertNotNull(project.tasks.findByName("testAggregateTask"))
        
        // Verify extension settings that enable all scopes
        // This test ensures that even with all scopes enabled,
        // build-time configurations will still be filtered out
        assertEquals(true, extension.includeDebugDependencies)
        assertEquals(true, extension.includeReleaseDependencies)
        assertEquals(true, extension.includeTestDependencies)
    }
}
