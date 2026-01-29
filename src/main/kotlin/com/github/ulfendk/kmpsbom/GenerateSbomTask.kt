package com.github.ulfendk.kmpsbom

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Dependency
import org.cyclonedx.model.Hash
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.Metadata
import org.cyclonedx.model.OrganizationalContact
import org.cyclonedx.generators.BomGeneratorFactory
import org.cyclonedx.CycloneDxSchema
import java.io.File

/**
 * Task that generates SBOM files for Kotlin Multiplatform targets
 */
abstract class GenerateSbomTask : DefaultTask() {
    
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    
    @get:Input
    @get:Optional
    abstract val targetPlatform: Property<String>
    
    @TaskAction
    fun generate() {
        val extension = project.extensions.getByType(KmpSbomExtension::class.java)
        
        if (targetPlatform.isPresent) {
            // Generate SBOM for specific target
            generateForTarget(targetPlatform.get(), extension)
        } else {
            // Generate SBOM for all targets
            generateForAllTargets(extension)
        }
    }
    
    private fun generateForAllTargets(extension: KmpSbomExtension) {
        val targetConfigs = findAllTargetConfigurations()
        
        if (targetConfigs.isEmpty()) {
            logger.warn("No Kotlin Multiplatform targets found. Generating SBOM for all configurations.")
            generateForConfiguration("all", project.configurations.filter { it.isCanBeResolved }, extension)
        } else {
            targetConfigs.forEach { (target, configs) ->
                generateForConfiguration(target, configs, extension)
            }
        }
    }
    
    private fun generateForTarget(target: String, extension: KmpSbomExtension) {
        val configs = findConfigurationsForTarget(target)
        generateForConfiguration(target, configs, extension)
    }
    
    private fun generateForConfiguration(
        targetName: String,
        configurations: List<Configuration>,
        extension: KmpSbomExtension
    ) {
        logger.lifecycle("Generating SBOM for target: $targetName")
        
        val collectionResult = collectDependencies(configurations)
        
        // Add Swift dependencies if configured and target is iOS
        val allDependencies = if (isIosTarget(targetName) && extension.packageResolvedPath != null) {
            val swiftDeps = collectSwiftDependencies(extension.packageResolvedPath!!)
            logger.lifecycle("Found ${swiftDeps.size} Swift package dependencies")
            collectionResult.dependencies + swiftDeps
        } else {
            collectionResult.dependencies
        }
        
        val components = mutableListOf<Component>()
        val dependencyGraph = collectionResult.dependencyGraph.toMutableMap()
        
        // Process each dependency
        allDependencies.forEach { dep ->
            val component = createComponent(dep, extension)
            components.add(component)
            
            // Ensure all dependencies have an entry in the graph (even if empty)
            val depId = dep.id
            dependencyGraph.putIfAbsent(depId, emptyList())
        }
        
        // Create BOM
        val bom = createBom(targetName, components, extension)
        
        // Add dependency graph
        addDependencyGraph(bom, dependencyGraph, components)
        
        // Scan for vulnerabilities if enabled
        if (extension.enableVulnerabilityScanning) {
            scanVulnerabilities(components, bom)
        }
        
        // Write SBOM files
        writeSbomFiles(bom, targetName)
    }
    
    private fun collectDependencies(configurations: List<Configuration>): DependencyCollectionResult {
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
                fun traverseDependencies(componentResult: org.gradle.api.artifacts.result.ResolvedComponentResult, parentId: String?, visitedInPath: Set<String>) {
                    val componentId = componentResult.id
                    
                    // Only process module components (not project dependencies)
                    if (componentId is ModuleComponentIdentifier) {
                        val id = "${componentId.group}:${componentId.module}:${componentId.version}"
                        
                        // Check for circular dependencies in current path
                        if (visitedInPath.contains(id)) {
                            return  // Stop recursion on circular dependency
                        }
                        
                        // Add to dependencies set if not already visited globally
                        if (!globalVisited.contains(id)) {
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
                        }
                        
                        // Record parent-child relationship
                        if (parentId != null) {
                            dependencyGraph.getOrPut(parentId) { mutableSetOf() }.add(id)
                        }
                        
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
    
    private fun collectSwiftDependencies(packageResolvedPath: String): Set<DependencyInfo> {
        val dependencies = mutableSetOf<DependencyInfo>()
        
        try {
            val packageResolvedFile = project.file(packageResolvedPath)
            val swiftPackages = SwiftPackageParser.parse(packageResolvedFile)
            
            swiftPackages.forEach { swiftPackage ->
                dependencies.add(swiftPackage.toDependencyInfo())
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse Swift Package.resolved file: ${e.message}")
        }
        
        return dependencies
    }
    
    private fun isIosTarget(targetName: String): Boolean {
        return targetName.contains("ios", ignoreCase = true)
    }
    
    private fun createComponent(dep: DependencyInfo, extension: KmpSbomExtension): Component {
        val component = Component()
        component.type = Component.Type.LIBRARY
        component.group = dep.group
        component.name = dep.name
        component.version = dep.version
        
        // Determine PURL type based on dependency source
        component.purl = if (dep.isSwiftPackage) {
            // Swift package - use swift purl type with namespace
            "pkg:swift/${dep.group}/${dep.name}@${dep.version}"
        } else {
            // Maven dependency - use maven purl type
            "pkg:maven/${dep.group}/${dep.name}@${dep.version}"
        }
        
        component.bomRef = dep.id
        
        // Add licenses if enabled
        if (extension.includeLicenses) {
            val licenseChoice = detectLicenses(dep)
            if (licenseChoice != null) {
                component.licenseChoice = licenseChoice
            }
        }
        
        // Add file hash
        if (dep.file != null && dep.file.exists()) {
            val hash = Hash(Hash.Algorithm.SHA_256, calculateSha256(dep.file))
            component.hashes = listOf(hash)
        }
        
        return component
    }
    
    private fun createBom(targetName: String, components: List<Component>, extension: KmpSbomExtension): Bom {
        val bom = Bom()
        bom.serialNumber = "urn:uuid:${java.util.UUID.randomUUID()}"
        bom.version = 1
        
        // Add metadata
        val metadata = Metadata()
        metadata.timestamp = java.util.Date()
        
        // Add organization info if provided
        if (extension.organizationName.isNotBlank()) {
            val org = OrganizationalContact()
            org.name = extension.organizationName
            metadata.authors = mutableListOf(org)
        }
        
        // Add component metadata for the project itself
        val projectComponent = Component()
        projectComponent.type = Component.Type.APPLICATION
        projectComponent.name = project.name
        projectComponent.version = project.version.toString()
        projectComponent.description = "Kotlin Multiplatform project - $targetName target"
        metadata.component = projectComponent
        
        bom.metadata = metadata
        bom.components = components
        
        return bom
    }
    
    private fun addDependencyGraph(bom: Bom, graph: Map<String, List<String>>, components: List<Component>) {
        val dependencies = mutableListOf<Dependency>()
        
        components.forEach { component ->
            val dependency = Dependency(component.bomRef)
            val deps = graph[component.bomRef] ?: emptyList()
            if (deps.isNotEmpty()) {
                dependency.dependencies = deps.map { Dependency(it) }
            }
            dependencies.add(dependency)
        }
        
        bom.dependencies = dependencies
    }
    
    private fun scanVulnerabilities(components: List<Component>, bom: Bom) {
        logger.lifecycle("Scanning for vulnerabilities...")
        val scanner = VulnerabilityScanner(project.logger)
        scanner.scan(components, bom)
    }
    
    private fun detectLicenses(dep: DependencyInfo): LicenseChoice? {
        // Try to detect license from POM file
        val pomFile = findPomFile(dep)
        if (pomFile != null && pomFile.exists()) {
            val licenseInfo = PomLicenseParser.parse(pomFile)
            if (licenseInfo != null) {
                val licenseChoice = LicenseChoice()
                val license = License()
                license.id = licenseInfo.id
                license.name = licenseInfo.name
                if (licenseInfo.url != null) {
                    license.url = licenseInfo.url
                }
                licenseChoice.addLicense(license)
                return licenseChoice
            }
        }
        return null
    }
    
    private fun findPomFile(dep: DependencyInfo): File? {
        // Look in Gradle cache for POM file
        val gradleHome = project.gradle.gradleUserHomeDir
        val pomDir = File(gradleHome, "caches/modules-2/files-2.1/${dep.group}/${dep.name}/${dep.version}")
        
        // Check if directory exists and is readable
        if (!pomDir.exists() || !pomDir.isDirectory) {
            return null
        }
        
        return pomDir.listFiles()?.firstOrNull { it.extension == "pom" }
    }
    
    private fun calculateSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    private fun writeSbomFiles(bom: Bom, targetName: String) {
        val outputDirectory = outputDir.get().asFile
        outputDirectory.mkdirs()
        
        // Write JSON format (primary FDA-approved format)  
        val jsonFile = File(outputDirectory, "sbom-$targetName.json")
        val jsonGenerator = BomGeneratorFactory.createJson(org.cyclonedx.Version.VERSION_16, bom)
        jsonFile.writeText(jsonGenerator.toJsonString())
        logger.lifecycle("Generated SBOM: ${jsonFile.absolutePath}")
        
        // Also write XML format for compatibility
        val xmlFile = File(outputDirectory, "sbom-$targetName.xml")
        val xmlGenerator = BomGeneratorFactory.createXml(org.cyclonedx.Version.VERSION_16, bom)
        xmlFile.writeText(xmlGenerator.toXmlString())
        logger.lifecycle("Generated SBOM: ${xmlFile.absolutePath}")
        
        // Write Markdown format for human readability
        val markdownFile = File(outputDirectory, "sbom-$targetName.md")
        val markdownContent = MarkdownBomGenerator.generate(bom)
        markdownFile.writeText(markdownContent)
        logger.lifecycle("Generated SBOM: ${markdownFile.absolutePath}")
    }
    
    private fun findAllTargetConfigurations(): Map<String, List<Configuration>> {
        val targetConfigs = mutableMapOf<String, MutableList<Configuration>>()
        
        // Common KMP configuration patterns
        val patterns = listOf(
            "androidDebug", "androidRelease",
            "iosArm64", "iosX64", "iosSimulatorArm64",
            "jvm", "js"
        )
        
        project.configurations.forEach { config ->
            if (config.isCanBeResolved) {
                patterns.forEach { pattern ->
                    if (config.name.contains(pattern, ignoreCase = true)) {
                        val targetName = pattern.lowercase()
                        targetConfigs.getOrPut(targetName) { mutableListOf() }.add(config)
                    }
                }
            }
        }
        
        return targetConfigs
    }
    
    private fun findConfigurationsForTarget(target: String): List<Configuration> {
        return project.configurations.filter { config ->
            config.isCanBeResolved && config.name.contains(target, ignoreCase = true)
        }
    }
}

data class DependencyInfo(
    val group: String,
    val name: String,
    val version: String,
    val id: String,
    val file: File?,
    val isSwiftPackage: Boolean = false
)

data class DependencyCollectionResult(
    val dependencies: Set<DependencyInfo>,
    val dependencyGraph: Map<String, List<String>>
)
