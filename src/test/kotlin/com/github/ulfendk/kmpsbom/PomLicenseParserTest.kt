package com.github.ulfendk.kmpsbom

import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
