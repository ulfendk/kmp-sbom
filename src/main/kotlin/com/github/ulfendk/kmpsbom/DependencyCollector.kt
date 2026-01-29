package com.github.ulfendk.kmpsbom

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Result of dependency collection containing both dependencies and their relationships
 */
data class DependencyCollectionResult(
    val dependencies: Set<DependencyInfo>,
    val dependencyGraph: Map<String, List<String>>
)

/**
 * Utility class for collecting dependencies and their relationships from Gradle configurations
 */
object DependencyCollector {
    
    /**
     * Collect dependencies from multiple configurations and build the dependency graph
     * 
     * @param configurations List of Gradle configurations to collect dependencies from
     * @param logger Logger for debug output
     * @return DependencyCollectionResult containing dependencies and their relationships
     */
    fun collectDependencies(
        configurations: List<Configuration>,
        logger: Logger
    ): DependencyCollectionResult {
        val dependencies = mutableSetOf<DependencyInfo>()
        val dependencyGraph = mutableMapOf<String, MutableSet<String>>()
        val fileCache = mutableMapOf<String, File?>()
        val globalVisited = mutableSetOf<String>() // Track visited across all configurations
        
        // Data class for iterative traversal to avoid stack overflow
        // Each entry contains: component, parent ID, and depth
        data class TraversalEntry(
            val component: org.gradle.api.artifacts.result.ResolvedComponentResult,
            val parentId: String?,
            val depth: Int
        )
        
        configurations.forEach { config ->
            try {
                // First, collect artifacts with files using the legacy API
                config.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    val componentId = artifact.id.componentIdentifier
                    if (componentId is ModuleComponentIdentifier) {
                        val id = "${componentId.group}:${componentId.module}:${componentId.version}"
                        fileCache[id] = artifact.file
                    }
                }
                
                // Then, extract dependency relationships using modern API
                val resolutionResult = config.incoming.resolutionResult
                
                // Iteratively traverse the dependency tree using a stack to avoid StackOverflowError
                val traversalStack = ArrayDeque<TraversalEntry>()
                traversalStack.addLast(TraversalEntry(resolutionResult.root, null, 0))
                
                while (traversalStack.isNotEmpty()) {
                    val (componentResult, parentId, depth) = traversalStack.removeLast()
                    
                    // Safety check for excessive depth
                    if (depth > 1000) {
                        logger.warn("Excessive traversal depth (${depth}) detected in configuration ${config.name}. Possible circular dependency.")
                        continue
                    }
                    
                    val componentId = componentResult.id
                    
                    // Only process module components (not project dependencies)
                    if (componentId is ModuleComponentIdentifier) {
                        val id = "${componentId.group}:${componentId.module}:${componentId.version}"
                        
                        // Check if already globally visited to avoid re-traversal and prevent cycles
                        if (globalVisited.contains(id)) {
                            // Skip this node entirely - don't record edge or re-traverse
                            // This prevents circular dependencies in the output graph
                            continue
                        }
                        
                        // Record parent-child relationship only for unvisited nodes
                        // This ensures the dependency graph is acyclic (DAG)
                        if (parentId != null) {
                            dependencyGraph.getOrPut(parentId) { mutableSetOf() }.add(id)
                        }
                        
                        // Add to dependencies set and mark as visited
                        globalVisited.add(id)
                        dependencies.add(
                            DependencyInfo(
                                group = componentId.group,
                                name = componentId.module,
                                version = componentId.version,
                                id = id,
                                file = fileCache[id]
                            )
                        )
                        
                        // Add transitive dependencies to stack for processing
                        componentResult.dependencies.forEach { depResult ->
                            if (depResult is ResolvedDependencyResult) {
                                traversalStack.addLast(TraversalEntry(depResult.selected, id, depth + 1))
                            }
                        }
                    } else {
                        // For project components, add their dependencies to stack without adding them to the graph
                        componentResult.dependencies.forEach { depResult ->
                            if (depResult is ResolvedDependencyResult) {
                                traversalStack.addLast(TraversalEntry(depResult.selected, parentId, depth + 1))
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                logger.warn("Error processing configuration ${config.name}: ${e.message}")
                logger.debug("Stack trace:", e)
            }
        }
        
        // Convert mutable sets to lists for the result
        val graphResult = dependencyGraph.mapValues { it.value.toList() }
        
        return DependencyCollectionResult(dependencies, graphResult)
    }
}
