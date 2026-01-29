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
        logger.lifecycle("Starting dependency collection from ${configurations.size} configurations")
        val dependencies = mutableSetOf<DependencyInfo>()
        val dependencyGraph = mutableMapOf<String, MutableSet<String>>()
        val fileCache = mutableMapOf<String, File?>()
        val globalVisited = mutableSetOf<String>() // Track visited across all configurations
        var processedConfigCount = 0
        
        configurations.forEach { config ->
            processedConfigCount++
            logger.lifecycle("Processing configuration [${processedConfigCount}/${configurations.size}]: ${config.name}")
            
            try {
                // First, collect artifacts with files using the legacy API
                var artifactCount = 0
                config.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    val componentId = artifact.id.componentIdentifier
                    if (componentId is ModuleComponentIdentifier) {
                        val id = "${componentId.group}:${componentId.module}:${componentId.version}"
                        fileCache[id] = artifact.file
                        artifactCount++
                    }
                }
                logger.debug("  Collected ${artifactCount} artifact files from ${config.name}")
                
                // Then, extract dependency relationships using modern API
                val resolutionResult = config.incoming.resolutionResult
                
                // Track traversal depth to detect potential issues
                var maxDepth = 0
                var nodeCount = 0
                
                // Iteratively traverse the dependency tree using a stack to avoid StackOverflowError
                // Each stack entry contains: component, parent ID, and depth
                data class TraversalEntry(
                    val component: org.gradle.api.artifacts.result.ResolvedComponentResult,
                    val parentId: String?,
                    val depth: Int
                )
                
                val traversalStack = ArrayDeque<TraversalEntry>()
                traversalStack.addLast(TraversalEntry(resolutionResult.root, null, 0))
                
                logger.debug("  Starting traversal for ${config.name}")
                
                while (traversalStack.isNotEmpty()) {
                    val (componentResult, parentId, depth) = traversalStack.removeLast()
                    
                    if (depth > maxDepth) {
                        maxDepth = depth
                    }
                    
                    // Safety check for excessive depth
                    if (depth > 1000) {
                        logger.warn("WARNING: Excessive traversal depth (${depth}) detected! Possible circular dependency. Current component: ${componentResult.id}")
                        logger.warn("  Parent: ${parentId}")
                        continue
                    }
                    
                    nodeCount++
                    if (nodeCount % 100 == 0) {
                        logger.debug("  Traversed ${nodeCount} nodes so far (current depth: ${depth})")
                    }
                    
                    val componentId = componentResult.id
                    
                    // Only process module components (not project dependencies)
                    if (componentId is ModuleComponentIdentifier) {
                        val id = "${componentId.group}:${componentId.module}:${componentId.version}"
                        
                        if (depth < 5) {
                            logger.debug("    [$depth] Processing dependency: $id (parent: $parentId)")
                        }
                        
                        // Record parent-child relationship
                        if (parentId != null) {
                            dependencyGraph.getOrPut(parentId) { mutableSetOf() }.add(id)
                        }
                        
                        // Check if already globally visited to avoid re-traversal and prevent cycles
                        if (globalVisited.contains(id)) {
                            // Edge recorded above, continue to avoid re-traversing already visited node
                            // This also prevents circular dependencies since we never re-traverse visited nodes
                            if (depth < 5) {
                                logger.debug("    [$depth] Already visited $id - skipping re-traversal")
                            }
                            continue
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
                        if (depth < 5) {
                            logger.debug("    [$depth] Project component (not included): ${componentResult.id}")
                        }
                        componentResult.dependencies.forEach { depResult ->
                            if (depResult is ResolvedDependencyResult) {
                                traversalStack.addLast(TraversalEntry(depResult.selected, parentId, depth + 1))
                            }
                        }
                    }
                }
                
                logger.lifecycle("  Completed ${config.name}: ${nodeCount} nodes, max depth: ${maxDepth}")
                
            } catch (e: Exception) {
                logger.warn("Error processing configuration ${config.name}: ${e.message}")
                logger.debug("Stack trace:", e)
            }
        }
        
        // Convert mutable sets to lists for the result
        val graphResult = dependencyGraph.mapValues { it.value.toList() }
        
        logger.lifecycle("Dependency collection complete: ${dependencies.size} unique dependencies, ${graphResult.size} graph entries")
        
        return DependencyCollectionResult(dependencies, graphResult)
    }
}
