package com.github.ulfendk.kmpsbom

import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals

class PomLicenseParserTest {
    
    @Test
    fun `parse returns null for non-existent file`() {
        val file = java.io.File("/non/existent/file.pom")
        val result = PomLicenseParser.parse(file)
        assertNull(result)
    }
    
    @Test
    fun `parse returns null for non-XML file`() {
        val file = kotlin.io.path.createTempFile("test", ".txt").toFile()
        file.writeText("not xml")
        val result = PomLicenseParser.parse(file)
        assertNull(result)
        file.delete()
    }
    
    @Test
    fun `parse extracts license from valid POM file`() {
        val file = kotlin.io.path.createTempFile("test", ".pom").toFile()
        try {
            file.writeText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>test-lib</artifactId>
                  <version>1.0.0</version>
                  <licenses>
                    <license>
                      <name>The Apache Software License, Version 2.0</name>
                      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                    </license>
                  </licenses>
                </project>
            """.trimIndent())
            
            val result = PomLicenseParser.parse(file)
            
            assertNotNull(result)
            assertEquals("Apache-2.0", result.id)
            assertEquals("The Apache Software License, Version 2.0", result.name)
            assertEquals("https://www.apache.org/licenses/LICENSE-2.0.txt", result.url)
        } finally {
            file.delete()
        }
    }
    
    @Test
    fun `parse maps MIT license correctly`() {
        val file = kotlin.io.path.createTempFile("test", ".pom").toFile()
        try {
            file.writeText("""
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
            
            val result = PomLicenseParser.parse(file)
            
            assertNotNull(result)
            assertEquals("MIT", result.id)
            assertEquals("MIT License", result.name)
            assertEquals("https://opensource.org/licenses/MIT", result.url)
        } finally {
            file.delete()
        }
    }
    
    @Test
    fun `parse returns null when no license element exists`() {
        val file = kotlin.io.path.createTempFile("test", ".pom").toFile()
        try {
            file.writeText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>test-lib</artifactId>
                  <version>1.0.0</version>
                </project>
            """.trimIndent())
            
            val result = PomLicenseParser.parse(file)
            
            assertNull(result)
        } finally {
            file.delete()
        }
    }
}
