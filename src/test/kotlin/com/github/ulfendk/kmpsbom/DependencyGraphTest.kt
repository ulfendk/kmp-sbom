package com.github.ulfendk.kmpsbom

import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.parsers.JsonParser
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class DependencyGraphTest {
    
    @Test
    fun `SBOM includes dependency graph with relationships`() {
        // Create a test project with some dependencies
        val project = ProjectBuilder.builder().build()
        
        // Apply the plugin
        project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        // Add a simple dependency (using Kotlin stdlib as example)
        project.repositories.mavenCentral()
        val configuration = project.configurations.create("testConfig")
        configuration.isCanBeResolved = true
        
        // Add Kotlin stdlib which has transitive dependencies
        project.dependencies.add("testConfig", "org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
        
        // Create the task
        val task = project.tasks.create("testGenerateSbom", GenerateSbomTask::class.java)
        task.outputDir.set(project.layout.buildDirectory.dir("test-sbom"))
        
        // Execute the task
        task.generate()
        
        // Verify the SBOM was generated
        val sbomFile = File(project.layout.buildDirectory.get().asFile, "test-sbom/sbom-all.json")
        assertTrue(sbomFile.exists(), "SBOM file should be generated")
        
        // Parse the SBOM
        val parser = JsonParser()
        val bom = parser.parse(sbomFile)
        
        assertNotNull(bom, "BOM should be parsed successfully")
        assertNotNull(bom.components, "BOM should have components")
        assertNotNull(bom.dependencies, "BOM should have dependencies graph")
        
        // Verify that the dependency graph is not empty
        assertTrue(bom.dependencies.isNotEmpty(), "Dependency graph should not be empty")
        
        // Verify that at least some dependencies have children
        val hasChildDependencies = bom.dependencies.any { dep ->
            dep.dependencies != null && dep.dependencies.isNotEmpty()
        }
        
        assertTrue(hasChildDependencies, "At least some dependencies should have child dependencies (transitive)")
    }
    
    @Test
    fun `dependency graph correctly maps parent-child relationships`() {
        // Create a test project
        val project = ProjectBuilder.builder().build()
        
        // Apply the plugin
        project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        // Add dependencies
        project.repositories.mavenCentral()
        val configuration = project.configurations.create("testConfig2")
        configuration.isCanBeResolved = true
        
        // Add a dependency with known transitive dependencies
        project.dependencies.add("testConfig2", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
        
        // Create the task
        val task = project.tasks.create("testGenerateSbom2", GenerateSbomTask::class.java)
        task.outputDir.set(project.layout.buildDirectory.dir("test-sbom2"))
        
        // Execute the task
        task.generate()
        
        // Verify the SBOM was generated
        val sbomFile = File(project.layout.buildDirectory.get().asFile, "test-sbom2/sbom-all.json")
        assertTrue(sbomFile.exists(), "SBOM file should be generated")
        
        // Parse the SBOM
        val parser = JsonParser()
        val bom = parser.parse(sbomFile)
        
        assertNotNull(bom.dependencies, "BOM should have dependency graph")
        
        // Count total dependencies and those with children
        val totalDeps = bom.dependencies.size
        val depsWithChildren = bom.dependencies.count { dep ->
            dep.dependencies != null && dep.dependencies.isNotEmpty()
        }
        
        // At least one dependency should have children (since kotlinx-coroutines has transitive deps)
        assertTrue(depsWithChildren > 0, "At least one dependency should have transitive dependencies")
        
        // The total number of dependency entries should match the number of components
        assertEquals(bom.components.size, totalDeps, 
            "Every component should have a dependency entry in the graph")
    }
    
    @Test
    fun `dependency references use correct bomRef identifiers`() {
        // Create a test project
        val project = ProjectBuilder.builder().build()
        
        // Apply the plugin
        project.pluginManager.apply("com.github.ulfendk.kmp-sbom")
        
        // Add dependencies
        project.repositories.mavenCentral()
        val configuration = project.configurations.create("testConfig3")
        configuration.isCanBeResolved = true
        
        project.dependencies.add("testConfig3", "org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
        
        // Create the task
        val task = project.tasks.create("testGenerateSbom3", GenerateSbomTask::class.java)
        task.outputDir.set(project.layout.buildDirectory.dir("test-sbom3"))
        
        // Execute the task
        task.generate()
        
        // Verify the SBOM was generated
        val sbomFile = File(project.layout.buildDirectory.get().asFile, "test-sbom3/sbom-all.json")
        assertTrue(sbomFile.exists(), "SBOM file should be generated")
        
        // Parse the SBOM
        val parser = JsonParser()
        val bom = parser.parse(sbomFile)
        
        // Create a set of all component bomRefs
        val componentRefs = bom.components.map { it.bomRef }.toSet()
        
        // Verify that all dependency references exist in the component list
        bom.dependencies.forEach { dep ->
            assertTrue(componentRefs.contains(dep.ref), 
                "Dependency ref ${dep.ref} should exist in components")
            
            // Check child dependencies too
            dep.dependencies?.forEach { childDep ->
                assertTrue(componentRefs.contains(childDep.ref),
                    "Child dependency ref ${childDep.ref} should exist in components")
            }
        }
    }
}
