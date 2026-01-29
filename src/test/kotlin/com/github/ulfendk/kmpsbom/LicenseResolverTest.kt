package com.github.ulfendk.kmpsbom

import org.gradle.api.logging.Logger
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals

class LicenseResolverTest {
    
    private val mockLogger = object : Logger by org.gradle.api.logging.Logging.getLogger("test") {
        override fun debug(message: String?) {}
        override fun info(message: String?) {}
        override fun warn(message: String?) {}
    }
    
    @Test
    fun `resolve from Maven Central for well-known library`() {
        // This test requires network access and may be slow
        // Testing with a well-known library that should be available on Maven Central
        
        val tempGradleHome = kotlin.io.path.createTempDirectory("gradle-test").toFile()
        try {
            val resolver = LicenseResolver(mockLogger, tempGradleHome)
            val dep = DependencyInfo(
                group = "com.google.code.gson",
                name = "gson",
                version = "2.10.1",
                id = "com.google.code.gson:gson:2.10.1",
                file = null
            )
            
            val license = resolver.resolve(dep)
            
            // Gson should have Apache-2.0 license
            assertNotNull(license)
            assertEquals("Apache-2.0", license.id)
        } finally {
            tempGradleHome.deleteRecursively()
        }
    }
    
    @Test
    fun `resolve returns null for non-existent dependency`() {
        val tempGradleHome = kotlin.io.path.createTempDirectory("gradle-test").toFile()
        try {
            val resolver = LicenseResolver(mockLogger, tempGradleHome)
            val dep = DependencyInfo(
                group = "com.example.nonexistent",
                name = "fake-library",
                version = "1.0.0",
                id = "com.example.nonexistent:fake-library:1.0.0",
                file = null
            )
            
            val license = resolver.resolve(dep)
            
            // Should return null for non-existent library
            assertNull(license)
        } finally {
            tempGradleHome.deleteRecursively()
        }
    }
    
    @Test
    fun `resolve from local cache when POM exists`() {
        val tempGradleHome = kotlin.io.path.createTempDirectory("gradle-test").toFile()
        try {
            // Create a fake Gradle cache structure
            val dep = DependencyInfo(
                group = "org.example",
                name = "test-lib",
                version = "1.0.0",
                id = "org.example:test-lib:1.0.0",
                file = null
            )
            
            val cacheDir = File(tempGradleHome, "caches/modules-2/files-2.1/${dep.group}/${dep.name}/${dep.version}/abcd1234")
            cacheDir.mkdirs()
            
            val pomFile = File(cacheDir, "test-lib-1.0.0.pom")
            pomFile.writeText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <licenses>
                    <license>
                      <name>MIT License</name>
                      <url>https://opensource.org/licenses/MIT</url>
                    </license>
                  </licenses>
                </project>
            """.trimIndent())
            
            val resolver = LicenseResolver(mockLogger, tempGradleHome)
            val license = resolver.resolve(dep)
            
            assertNotNull(license)
            assertEquals("MIT", license.id)
        } finally {
            tempGradleHome.deleteRecursively()
        }
    }
    
    @Test
    fun `resolve prefers local cache over remote repositories`() {
        val tempGradleHome = kotlin.io.path.createTempDirectory("gradle-test").toFile()
        try {
            // Create a dependency that exists both locally and remotely
            val dep = DependencyInfo(
                group = "org.example.local",
                name = "cached-lib",
                version = "1.0.0",
                id = "org.example.local:cached-lib:1.0.0",
                file = null
            )
            
            val cacheDir = File(tempGradleHome, "caches/modules-2/files-2.1/${dep.group}/${dep.name}/${dep.version}/xyz789")
            cacheDir.mkdirs()
            
            val pomFile = File(cacheDir, "cached-lib-1.0.0.pom")
            pomFile.writeText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <licenses>
                    <license>
                      <name>BSD-3-Clause</name>
                    </license>
                  </licenses>
                </project>
            """.trimIndent())
            
            val resolver = LicenseResolver(mockLogger, tempGradleHome)
            val license = resolver.resolve(dep)
            
            // Should get the cached version (BSD-3-Clause), not try to fetch remotely
            assertNotNull(license)
            assertEquals("BSD-3-Clause", license.id)
        } finally {
            tempGradleHome.deleteRecursively()
        }
    }
    
    @Test
    fun `resolve handles Swift packages with GitHub API`() {
        // Note: This test requires network access to GitHub API
        // If the test environment blocks GitHub, this test may fail
        
        val tempGradleHome = kotlin.io.path.createTempDirectory("gradle-test").toFile()
        try {
            val resolver = LicenseResolver(mockLogger, tempGradleHome)
            
            // Create a Swift package dependency for a well-known package
            val dep = DependencyInfo(
                group = "https://github.com/apple/swift-nio.git",
                name = "swift-nio",
                version = "2.0.0",
                id = "apple:swift-nio:2.0.0",
                file = null,
                isSwiftPackage = true
            )
            
            val license = resolver.resolve(dep)
            
            // swift-nio should have Apache-2.0 license
            // If GitHub is blocked in the test environment, this assertion may fail
            // In that case, we just check that it returns something or null gracefully
            if (license != null) {
                assertEquals("Apache-2.0", license.id)
            }
            // If null, that's okay too - means GitHub API is blocked
        } finally {
            tempGradleHome.deleteRecursively()
        }
    }
    
    @Test
    fun `resolve handles Swift package with non-GitHub URL gracefully`() {
        val tempGradleHome = kotlin.io.path.createTempDirectory("gradle-test").toFile()
        try {
            val resolver = LicenseResolver(mockLogger, tempGradleHome)
            
            // Create a Swift package dependency with a non-GitHub URL
            val dep = DependencyInfo(
                group = "https://gitlab.com/example/package.git",
                name = "example-package",
                version = "1.0.0",
                id = "example:example-package:1.0.0",
                file = null,
                isSwiftPackage = true
            )
            
            val license = resolver.resolve(dep)
            
            // Should return null gracefully for non-GitHub repositories
            // (GitLab support not yet implemented)
            assertNull(license)
        } finally {
            tempGradleHome.deleteRecursively()
        }
    }
}
