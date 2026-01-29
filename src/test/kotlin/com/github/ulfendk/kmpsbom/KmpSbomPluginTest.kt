package com.github.ulfendk.kmpsbom

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KmpSbomPluginTest {
    
    @Test
    fun `plugin registers generateSbom task`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        assertNotNull(project.tasks.findByName("generateSbom"))
    }
    
    @Test
    fun `plugin creates kmpSbom extension`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        val extension = project.extensions.findByName("kmpSbom")
        assertNotNull(extension)
        assertTrue(extension is KmpSbomExtension)
    }
    
    @Test
    fun `extension has default values`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        val extension = project.extensions.getByType(KmpSbomExtension::class.java)
        assertTrue(extension.enableVulnerabilityScanning)
        assertTrue(extension.includeLicenses)
        kotlin.test.assertEquals("1.5", extension.sbomVersion)
    }
}
