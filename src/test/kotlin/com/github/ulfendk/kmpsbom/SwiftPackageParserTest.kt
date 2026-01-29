package com.github.ulfendk.kmpsbom

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwiftPackageParserTest {
    
    @Test
    fun `parse valid Package resolved file`() {
        // Create a temporary Package.resolved file
        val tempFile = File.createTempFile("Package", ".resolved")
        tempFile.deleteOnExit()
        
        val packageResolvedContent = """
        {
          "pins" : [
            {
              "identity" : "app-check",
              "kind" : "remoteSourceControl",
              "location" : "https://github.com/google/app-check.git",
              "state" : {
                "revision" : "61b85103a1aeed8218f17c794687781505fbbef5",
                "version" : "11.2.0"
              }
            },
            {
              "identity" : "firebase-ios-sdk",
              "kind" : "remoteSourceControl",
              "location" : "https://www.github.com/firebase/firebase-ios-sdk.git",
              "state" : {
                "revision" : "1cce11cf94d27e2fc194112cc7ad51e8fb279230",
                "version" : "12.3.0"
              }
            }
          ]
        }
        """.trimIndent()
        
        tempFile.writeText(packageResolvedContent)
        
        // Parse the file
        val packages = SwiftPackageParser.parse(tempFile)
        
        // Verify results
        assertEquals(2, packages.size)
        
        val appCheck = packages.find { it.identity == "app-check" }
        assertNotNull(appCheck)
        assertEquals("11.2.0", appCheck.version)
        assertEquals("https://github.com/google/app-check.git", appCheck.location)
        assertEquals("61b85103a1aeed8218f17c794687781505fbbef5", appCheck.revision)
        
        val firebase = packages.find { it.identity == "firebase-ios-sdk" }
        assertNotNull(firebase)
        assertEquals("12.3.0", firebase.version)
        assertEquals("https://www.github.com/firebase/firebase-ios-sdk.git", firebase.location)
    }
    
    @Test
    fun `parse Package resolved with object wrapper`() {
        // Create a temporary Package.resolved file with version 2 format
        val tempFile = File.createTempFile("Package", ".resolved")
        tempFile.deleteOnExit()
        
        val packageResolvedContent = """
        {
          "object": {
            "pins" : [
              {
                "identity" : "google-ads-sdk",
                "kind" : "remoteSourceControl",
                "location" : "https://github.com/googleads/google-ads-on-device-conversion-ios-sdk",
                "state" : {
                  "revision" : "c7d04b7592d3a1d6f8b7ce4e103cfbcbd766f419",
                  "version" : "3.0.0"
                }
              }
            ]
          }
        }
        """.trimIndent()
        
        tempFile.writeText(packageResolvedContent)
        
        // Parse the file
        val packages = SwiftPackageParser.parse(tempFile)
        
        // Verify results
        assertEquals(1, packages.size)
        
        val googleAds = packages.first()
        assertEquals("google-ads-sdk", googleAds.identity)
        assertEquals("3.0.0", googleAds.version)
    }
    
    @Test
    fun `parse non-existent file returns empty list`() {
        val nonExistentFile = File("/tmp/does-not-exist-Package.resolved")
        val packages = SwiftPackageParser.parse(nonExistentFile)
        
        assertTrue(packages.isEmpty())
    }
    
    @Test
    fun `toDependencyInfo extracts group from GitHub URL`() {
        val swiftPackage = SwiftPackageInfo(
            identity = "app-check",
            location = "https://github.com/google/app-check.git",
            version = "11.2.0",
            revision = "abc123",
            kind = "remoteSourceControl"
        )
        
        val depInfo = swiftPackage.toDependencyInfo()
        
        assertEquals("google", depInfo.group)
        assertEquals("app-check", depInfo.name)
        assertEquals("11.2.0", depInfo.version)
        assertEquals("google:app-check:11.2.0", depInfo.id)
    }
    
    @Test
    fun `toDependencyInfo uses swift as default group`() {
        val swiftPackage = SwiftPackageInfo(
            identity = "some-package",
            location = "https://example.com/some-package.git",
            version = "1.0.0",
            revision = "def456",
            kind = "remoteSourceControl"
        )
        
        val depInfo = swiftPackage.toDependencyInfo()
        
        assertEquals("swift", depInfo.group)
        assertEquals("some-package", depInfo.name)
    }
}
