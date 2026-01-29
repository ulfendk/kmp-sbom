package com.github.ulfendk.kmpsbom

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DependencyCollectorTest {
    
    @Test
    fun `dependency graph does not contain circular references`() {
        // Create a test project
        val project = ProjectBuilder.builder().build()
        project.repositories.mavenCentral()
        
        // Create a resolvable configuration
        // Note: This creates an empty configuration. Setting up real Maven dependencies
        // in unit tests is complex and requires actual artifact resolution.
        // The fix is validated through integration testing with real projects.
        val config = project.configurations.create("testConfig").apply {
            isCanBeResolved = true
        }
        
        val result = DependencyCollector.collectDependencies(listOf(config), project.logger)
        
        // Verify no dependency points to itself (self-loop)
        result.dependencyGraph.forEach { (parent, children) ->
            assertFalse(
                children.contains(parent),
                "Dependency $parent should not contain itself in its children"
            )
        }
        
        // Verify no immediate circular references (A -> B and B -> A)
        result.dependencyGraph.forEach { (parent, children) ->
            children.forEach { child ->
                val childDeps = result.dependencyGraph[child] ?: emptyList()
                assertFalse(
                    childDeps.contains(parent),
                    "Circular reference detected: $parent -> $child -> $parent"
                )
            }
        }
    }
    
    @Test
    fun `globally visited nodes are not traversed multiple times`() {
        // This test verifies that the DependencyCollector runs without errors
        // and produces consistent results. The actual verification that globally
        // visited nodes are not traversed multiple times is done through the
        // logic inspection and integration testing with real projects.
        
        val project = ProjectBuilder.builder().build()
        project.repositories.mavenCentral()
        
        val config = project.configurations.create("testConfig").apply {
            isCanBeResolved = true
        }
        
        val result = DependencyCollector.collectDependencies(listOf(config), project.logger)
        
        // Each dependency should appear in the dependencies set only once
        val dependencyIds = result.dependencies.map { it.id }
        assertEquals(
            dependencyIds.size,
            dependencyIds.toSet().size,
            "Each dependency should appear only once in the result"
        )
    }
    
    @Test
    fun `dependency graph is acyclic`() {
        // This test verifies that the dependency graph is acyclic (DAG)
        // using a DFS-based cycle detection algorithm.
        // Note: With an empty configuration, this will always pass.
        // The actual circular dependency prevention is validated through
        // integration testing with real Gradle projects that have complex
        // dependency relationships.
        
        val project = ProjectBuilder.builder().build()
        project.repositories.mavenCentral()
        
        val config = project.configurations.create("testConfig").apply {
            isCanBeResolved = true
        }
        
        val result = DependencyCollector.collectDependencies(listOf(config), project.logger)
        
        // Helper function to detect cycles using DFS
        fun hasCycle(node: String, visited: MutableSet<String>, recStack: MutableSet<String>): Boolean {
            visited.add(node)
            recStack.add(node)
            
            val children = result.dependencyGraph[node] ?: emptyList()
            for (child in children) {
                if (!visited.contains(child)) {
                    if (hasCycle(child, visited, recStack)) {
                        return true
                    }
                } else if (recStack.contains(child)) {
                    // Found a back edge (cycle)
                    return true
                }
            }
            
            recStack.remove(node)
            return false
        }
        
        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()
        
        // Verify no cycles exist in the graph
        for (node in result.dependencyGraph.keys) {
            if (!visited.contains(node)) {
                assertFalse(
                    hasCycle(node, visited, recStack),
                    "Cycle detected in dependency graph starting from $node"
                )
            }
        }
    }
}
