package com.github.ulfendk.kmpsbom

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwiftPackageIntegrationTest {
    
    @Test
    fun `generateSbom includes Swift dependencies for iOS targets`() {
        // Create a test project
        val projectDir = File.createTempFile("test-project", "").apply {
            delete()
            mkdirs()
        }
        
        try {
            // Create a Package.resolved file
            val packageResolved = File(projectDir, "Package.resolved")
            packageResolved.writeText("""
                {
                  "pins" : [
                    {
                      "identity" : "firebase-ios-sdk",
                      "kind" : "remoteSourceControl",
                      "location" : "https://github.com/firebase/firebase-ios-sdk.git",
                      "state" : {
                        "revision" : "1cce11cf94d27e2fc194112cc7ad51e8fb279230",
                        "version" : "12.3.0"
                      }
                    }
                  ]
                }
            """.trimIndent())
            
            // Create a Gradle project
            val project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
            
            project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
            
            // Configure extension
            val extension = project.extensions.getByType(KmpSbomExtension::class.java)
            extension.packageResolvedPath = packageResolved.absolutePath
            
            // Get the task
            val task = project.tasks.getByName("generateSbom") as GenerateSbomTask
            task.outputDir.set(File(projectDir, "build/sbom"))
            
            // Verify Swift parser works
            val packages = SwiftPackageParser.parse(packageResolved)
            assertEquals(1, packages.size)
            
            val swiftPackage = packages.first()
            assertEquals("firebase-ios-sdk", swiftPackage.identity)
            assertEquals("12.3.0", swiftPackage.version)
            
            // Verify conversion to DependencyInfo
            val depInfo = swiftPackage.toDependencyInfo()
            assertEquals("firebase", depInfo.group)
            assertEquals("firebase-ios-sdk", depInfo.name)
            assertTrue(depInfo.isSwiftPackage)
            
        } finally {
            projectDir.deleteRecursively()
        }
    }
    
    @Test
    fun `Swift package generates correct PURL format`() {
        val swiftPackage = SwiftPackageInfo(
            identity = "firebase-ios-sdk",
            location = "https://github.com/firebase/firebase-ios-sdk.git",
            version = "12.3.0",
            revision = "abc123",
            kind = "remoteSourceControl"
        )
        
        val depInfo = swiftPackage.toDependencyInfo()
        
        // Create a temporary project to test component creation
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        val extension = project.extensions.getByType(KmpSbomExtension::class.java)
        val task = project.tasks.getByName("generateSbom") as GenerateSbomTask
        
        // Use reflection to call the private createComponent method
        val method = GenerateSbomTask::class.java.getDeclaredMethod(
            "createComponent",
            DependencyInfo::class.java,
            KmpSbomExtension::class.java
        )
        method.isAccessible = true
        
        val component = method.invoke(task, depInfo, extension) as org.cyclonedx.model.Component
        
        // Verify PURL format includes namespace
        assertEquals("pkg:swift/firebase/firebase-ios-sdk@12.3.0", component.purl)
    }
}
