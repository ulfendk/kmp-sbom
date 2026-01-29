package com.github.ulfendk.kmpsbom

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
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
import java.io.File

/**
 * Task that generates aggregate SBOM files for configured app/framework modules.
 * This task collects dependencies from all transitive project dependencies.
 */
abstract class GenerateAggregateSbomTask : DefaultTask() {
    
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    
    @get:Input
    abstract val targetPlatform: Property<String>
    
    @get:Input
    @get:Optional
    abstract val moduleProject: Property<String>
    
    @TaskAction
    fun generate() {
        val extension = project.extensions.getByType(KmpSbomExtension::class.java)
        val targetName = targetPlatform.get()
        
        logger.lifecycle("Generating aggregate SBOM for $targetName")
        
        // Find the module project
        val modulePath = moduleProject.orNull
        val targetProject = if (modulePath != null) {
            project.findProject(modulePath)
        } else {
            project
        }
        
        if (targetProject == null) {
            logger.warn("Module project not found: $modulePath, using root project")
            generateAggregateSbom(project, targetName, extension)
        } else {
            generateAggregateSbom(targetProject, targetName, extension)
        }
    }
    
    private fun generateAggregateSbom(
        targetProject: Project,
        targetName: String,
        extension: KmpSbomExtension
    ) {
        // Collect all project dependencies (modules)
        val allProjects = collectAllProjectDependencies(targetProject)
        logger.lifecycle("Found ${allProjects.size} modules to include in aggregate SBOM")
        
        // Collect configurations from all projects
        val allConfigurations = mutableListOf<Configuration>()
        allProjects.forEach { proj ->
            val configs = findConfigurationsForTarget(proj, targetName, extension)
            allConfigurations.addAll(configs)
        }
        
        logger.lifecycle("Collecting dependencies from ${allConfigurations.size} configurations")
        
        // Collect all dependencies
        val dependencies = collectDependencies(allConfigurations)
        
        // Add Swift dependencies if configured and target is iOS
        val allDependencies = if (isIosTarget(targetName) && extension.packageResolvedPath != null) {
            val swiftDeps = collectSwiftDependencies(extension.packageResolvedPath!!)
            logger.lifecycle("Found ${swiftDeps.size} Swift package dependencies")
            dependencies + swiftDeps
        } else {
            dependencies
        }
        
        logger.lifecycle("Total dependencies found: ${allDependencies.size}")
        
        val components = mutableListOf<Component>()
        val dependencyGraph = mutableMapOf<String, MutableList<String>>()
        
        // Process each dependency
        allDependencies.forEach { dep ->
            val component = createComponent(dep, extension)
            components.add(component)
            
            // Track dependency relationships
            val depId = dep.id
            dependencyGraph.putIfAbsent(depId, mutableListOf())
        }
        
        // Create BOM
        val bom = createBom(targetName, targetProject.name, components, extension)
        
        // Add dependency graph
        addDependencyGraph(bom, dependencyGraph, components)
        
        // Scan for vulnerabilities if enabled
        if (extension.enableVulnerabilityScanning) {
            scanVulnerabilities(components, bom)
        }
        
        // Write SBOM files
        writeSbomFiles(bom, targetName)
    }
    
    /**
     * Collect all project dependencies recursively
     */
    private fun collectAllProjectDependencies(project: Project): Set<Project> {
        val projects = mutableSetOf<Project>()
        projects.add(project)
        
        val queue = mutableListOf(project)
        val visited = mutableSetOf<Project>()
        
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            if (current in visited) continue
            visited.add(current)
            
            // Get all configurations for this project
            current.configurations.forEach { config ->
                if (config.isCanBeResolved) {
                    try {
                        config.allDependencies.forEach { dep ->
                            if (dep is org.gradle.api.artifacts.ProjectDependency) {
                                val depProject = dep.dependencyProject
                                if (depProject !in visited) {
                                    projects.add(depProject)
                                    queue.add(depProject)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Could not resolve configuration ${config.name}: ${e.message}")
                    }
                }
            }
        }
        
        return projects
    }
    
    private fun findConfigurationsForTarget(
        proj: Project,
        target: String,
        extension: KmpSbomExtension
    ): List<Configuration> {
        val configs = mutableListOf<Configuration>()
        
        proj.configurations.forEach { config ->
            if (!config.isCanBeResolved) return@forEach
            
            val configName = config.name.lowercase()
            
            // Check if this configuration matches the target
            if (!configName.contains(target.lowercase())) {
                return@forEach
            }
            
            // Filter based on scope preferences
            val shouldInclude = when {
                // Skip test configurations if not included
                configName.contains("test") -> extension.includeTestDependencies
                
                // Check for debug configurations
                configName.contains("debug") -> extension.includeDebugDependencies
                
                // Check for release configurations
                configName.contains("release") -> extension.includeReleaseDependencies
                
                // Include non-debug/non-release/non-test configurations by default
                else -> true
            }
            
            if (shouldInclude) {
                configs.add(config)
            }
        }
        
        return configs
    }
    
    private fun collectDependencies(configurations: List<Configuration>): Set<DependencyInfo> {
        val dependencies = mutableSetOf<DependencyInfo>()
        
        configurations.forEach { config ->
            try {
                config.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    val componentId = artifact.id.componentIdentifier
                    if (componentId is ModuleComponentIdentifier) {
                        dependencies.add(
                            DependencyInfo(
                                group = componentId.group,
                                name = componentId.module,
                                version = componentId.version,
                                id = "${componentId.group}:${componentId.module}:${componentId.version}",
                                file = artifact.file
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                logger.debug("Could not resolve configuration ${config.name}: ${e.message}")
            }
        }
        
        return dependencies
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
    
    private fun createBom(
        targetName: String,
        projectName: String,
        components: List<Component>,
        extension: KmpSbomExtension
    ): Bom {
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
        projectComponent.name = projectName
        projectComponent.version = project.version.toString()
        projectComponent.description = "Aggregate SBOM for $targetName"
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
        val jsonFile = File(outputDirectory, "sbom-$targetName-aggregate.json")
        val jsonGenerator = BomGeneratorFactory.createJson(org.cyclonedx.Version.VERSION_16, bom)
        jsonFile.writeText(jsonGenerator.toJsonString())
        logger.lifecycle("Generated aggregate SBOM: ${jsonFile.absolutePath}")
        
        // Also write XML format for compatibility
        val xmlFile = File(outputDirectory, "sbom-$targetName-aggregate.xml")
        val xmlGenerator = BomGeneratorFactory.createXml(org.cyclonedx.Version.VERSION_16, bom)
        xmlFile.writeText(xmlGenerator.toXmlString())
        logger.lifecycle("Generated aggregate SBOM: ${xmlFile.absolutePath}")
    }
}
