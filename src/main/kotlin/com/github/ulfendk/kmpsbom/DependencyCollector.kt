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
                
                // Recursively traverse the dependency tree
                fun traverseDependencies(
                    componentResult: org.gradle.api.artifacts.result.ResolvedComponentResult,
                    parentId: String?,
                    visitedInPath: Set<String>
                ) {
                    val componentId = componentResult.id
                    
                    // Only process module components (not project dependencies)
                    if (componentId is ModuleComponentIdentifier) {
                        val id = "${componentId.group}:${componentId.module}:${componentId.version}"
                        
                        // Check for circular dependencies in current path
                        if (visitedInPath.contains(id)) {
                            return  // Stop recursion on circular dependency
                        }
                        
                        // Record parent-child relationship
                        if (parentId != null) {
                            dependencyGraph.getOrPut(parentId) { mutableSetOf() }.add(id)
                        }
                        
                        // Check if already globally visited to avoid re-traversal
                        if (globalVisited.contains(id)) {
                            // Edge recorded above, return early to avoid re-traversing already visited node
                            return
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
                        
                        // Process transitive dependencies with updated path
                        val newVisitedInPath = visitedInPath + id
                        componentResult.dependencies.forEach { depResult ->
                            if (depResult is ResolvedDependencyResult) {
                                traverseDependencies(depResult.selected, id, newVisitedInPath)
                            }
                        }
                    } else {
                        // For project components, process their dependencies without adding them to the graph
                        componentResult.dependencies.forEach { depResult ->
                            if (depResult is ResolvedDependencyResult) {
                                traverseDependencies(depResult.selected, parentId, visitedInPath)
                            }
                        }
                    }
                }
                
                // Start traversal from root (which is typically a project component)
                traverseDependencies(resolutionResult.root, null, emptySet())
                
            } catch (e: Exception) {
                logger.debug("Could not resolve configuration ${config.name}: ${e.message}")
            }
        }
        
        // Convert mutable sets to lists for the result
        val graphResult = dependencyGraph.mapValues { it.value.toList() }
        
        return DependencyCollectionResult(dependencies, graphResult)
    }
}
