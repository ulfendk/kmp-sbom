package com.github.ulfendk.kmpsbom

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
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
        val targetProject = when {
            modulePath == null -> project
            modulePath == "." -> project.rootProject
            else -> project.findProject(modulePath)
        }
        
        if (targetProject == null) {
            logger.warn("Module project not found: $modulePath, using root project")
            generateAggregateSbom(project.rootProject, targetName, extension)
        } else {
            generateAggregateSbom(targetProject, targetName, extension)
        }
    }
    
    private fun generateAggregateSbom(
        targetProject: Project,
        targetName: String,
        extension: KmpSbomExtension
    ) {
        try {
            // Collect all project dependencies (modules)
            logger.lifecycle("=".repeat(80))
            logger.lifecycle("Step 1: Collecting project dependencies")
            logger.lifecycle("=".repeat(80))
            val allProjects = collectAllProjectDependencies(targetProject)
            logger.lifecycle("Found ${allProjects.size} modules to include in aggregate SBOM")
            
            // Collect configurations from all projects
            logger.lifecycle("")
            logger.lifecycle("=".repeat(80))
            logger.lifecycle("Step 2: Collecting configurations from all projects")
            logger.lifecycle("=".repeat(80))
            val allConfigurations = mutableListOf<Configuration>()
            allProjects.forEachIndexed { index, proj ->
                logger.lifecycle("Finding configs for project [${index + 1}/${allProjects.size}]: ${proj.path}")
                val configs = findConfigurationsForTarget(proj, targetName, extension)
                logger.lifecycle("  -> Found ${configs.size} configurations")
                allConfigurations.addAll(configs)
            }
            
            logger.lifecycle("")
            logger.lifecycle("Collecting dependencies from ${allConfigurations.size} configurations")
            
            // Collect all dependencies
            logger.lifecycle("")
            logger.lifecycle("=".repeat(80))
            logger.lifecycle("Step 3: Collecting dependencies from configurations")
            logger.lifecycle("=".repeat(80))
            val collectionResult = DependencyCollector.collectDependencies(allConfigurations, logger)
            
            // Add Swift dependencies if configured and target is iOS
            logger.lifecycle("")
            logger.lifecycle("=".repeat(80))
            logger.lifecycle("Step 4: Adding platform-specific dependencies")
            logger.lifecycle("=".repeat(80))
            val allDependencies = if (isIosTarget(targetName) && extension.packageResolvedPath != null) {
                val swiftDeps = collectSwiftDependencies(extension.packageResolvedPath!!)
                logger.lifecycle("Found ${swiftDeps.size} Swift package dependencies")
                collectionResult.dependencies + swiftDeps
            } else {
                logger.lifecycle("No additional platform-specific dependencies")
                collectionResult.dependencies
            }
            
            logger.lifecycle("")
            logger.lifecycle("Total dependencies found: ${allDependencies.size}")
            
            logger.lifecycle("")
            logger.lifecycle("=".repeat(80))
            logger.lifecycle("Step 5: Creating SBOM components")
            logger.lifecycle("=".repeat(80))
            val components = mutableListOf<Component>()
            val dependencyGraph = collectionResult.dependencyGraph.toMutableMap()
            
            // Process each dependency
            allDependencies.forEachIndexed { index, dep ->
                if ((index + 1) % 50 == 0 || index == 0) {
                    logger.lifecycle("Creating component [${index + 1}/${allDependencies.size}]...")
                }
                val component = createComponent(dep, extension)
                components.add(component)
                
                // Ensure all dependencies have an entry in the graph (even if empty)
                val depId = dep.id
                dependencyGraph.putIfAbsent(depId, emptyList())
            }
            logger.lifecycle("Created ${components.size} components")
            
            // Create BOM
            logger.lifecycle("")
            logger.lifecycle("=".repeat(80))
            logger.lifecycle("Step 6: Creating BOM structure")
            logger.lifecycle("=".repeat(80))
            val bom = createBom(targetName, targetProject.name, components, extension)
            
            // Add dependency graph
            logger.lifecycle("Adding dependency graph with ${dependencyGraph.size} entries")
            addDependencyGraph(bom, dependencyGraph, components)
            
            // Scan for vulnerabilities if enabled
            if (extension.enableVulnerabilityScanning) {
                logger.lifecycle("")
                logger.lifecycle("=".repeat(80))
                logger.lifecycle("Step 7: Scanning for vulnerabilities")
                logger.lifecycle("=".repeat(80))
                scanVulnerabilities(components, bom)
            }
            
            // Write SBOM files
            logger.lifecycle("")
            logger.lifecycle("=".repeat(80))
            logger.lifecycle("Step 8: Writing SBOM files")
            logger.lifecycle("=".repeat(80))
            writeSbomFiles(bom, targetName)
            
            logger.lifecycle("")
            logger.lifecycle("=".repeat(80))
            logger.lifecycle("SBOM generation completed successfully!")
            logger.lifecycle("=".repeat(80))
        } catch (e: StackOverflowError) {
            logger.error("=".repeat(80))
            logger.error("STACK OVERFLOW ERROR DETECTED!")
            logger.error("=".repeat(80))
            logger.error("This typically indicates a circular dependency or excessive recursion.")
            logger.error("Please check the log above for the last processed items before failure.")
            logger.error("Consider:")
            logger.error("  1. Checking for circular project dependencies in your build")
            logger.error("  2. Reducing the number of configurations being processed")
            logger.error("  3. Breaking circular dependencies between modules")
            logger.error("=".repeat(80))
            throw e
        } catch (e: Exception) {
            logger.error("=".repeat(80))
            logger.error("ERROR during SBOM generation: ${e.message}")
            logger.error("=".repeat(80))
            logger.debug("Stack trace:", e)
            throw e
        }
    }
    
    /**
     * Collect all project dependencies recursively
     */
    private fun collectAllProjectDependencies(project: Project): Set<Project> {
        logger.lifecycle("Starting to collect project dependencies from: ${project.path}")
        val projects = mutableSetOf<Project>()
        projects.add(project)
        
        val queue = mutableListOf(project)
        val visited = mutableSetOf<Project>()
        var processedCount = 0
        
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            if (current in visited) {
                logger.debug("Skipping already visited project: ${current.path}")
                continue
            }
            visited.add(current)
            processedCount++
            
            logger.lifecycle("Processing project [${processedCount}]: ${current.path}")
            
            // Get all configurations for this project
            var configCount = 0
            current.configurations.forEach { config ->
                if (config.isCanBeResolved) {
                    configCount++
                    try {
                        var projectDepCount = 0
                        config.allDependencies.forEach { dep ->
                            if (dep is org.gradle.api.artifacts.ProjectDependency) {
                                projectDepCount++
                                val depProject = dep.dependencyProject
                                logger.debug("  Found project dependency: ${depProject.path} (via config: ${config.name})")
                                if (depProject !in visited) {
                                    projects.add(depProject)
                                    queue.add(depProject)
                                    logger.lifecycle("  Added new project to queue: ${depProject.path}")
                                } else {
                                    logger.debug("  Project ${depProject.path} already visited")
                                }
                            }
                        }
                        if (projectDepCount > 0) {
                            logger.debug("  Config ${config.name} had ${projectDepCount} project dependencies")
                        }
                    } catch (e: Exception) {
                        logger.debug("Could not resolve configuration ${config.name}: ${e.message}")
                    }
                }
            }
            logger.debug("  Checked ${configCount} resolvable configurations in ${current.path}")
        }
        
        logger.lifecycle("Finished collecting project dependencies. Total projects found: ${projects.size}")
        projects.forEachIndexed { index, proj ->
            logger.lifecycle("  Project [${index + 1}]: ${proj.path}")
        }
        
        return projects
    }
    
    private fun findConfigurationsForTarget(
        proj: Project,
        target: String,
        extension: KmpSbomExtension
    ): List<Configuration> {
        logger.debug("Finding configurations for target '$target' in project ${proj.path}")
        val configs = mutableListOf<Configuration>()
        var totalConfigs = 0
        var resolvableConfigs = 0
        var matchedConfigs = 0
        var includedConfigs = 0
        
        proj.configurations.forEach { config ->
            totalConfigs++
            if (!config.isCanBeResolved) {
                logger.debug("  Skipping non-resolvable config: ${config.name}")
                return@forEach
            }
            resolvableConfigs++
            
            val configName = config.name.lowercase()
            
            // Check if this configuration matches the target
            val targetLower = target.lowercase()
            val matchesTarget = when {
                // Android target can use android* or jvm configurations
                targetLower == "android" -> 
                    configName.contains("android") || configName.contains("jvm")
                // iOS target matches ios* configurations
                targetLower == "ios" -> 
                    configName.contains("ios")
                // Other targets - direct match
                else -> configName.contains(targetLower)
            }
            
            if (!matchesTarget) {
                logger.debug("  Config '${config.name}' doesn't match target '$target'")
                return@forEach
            }
            matchedConfigs++
            
            // Skip build-time only configurations (not part of runtime binary)
            if (configName.contains("compileonly") || 
                configName.contains("kapt") || 
                configName.contains("ksp") ||
                configName.contains("annotationprocessor") ||
                configName.contains("provided")) {
                logger.debug("  Skipping build-time-only config: ${config.name}")
                return@forEach
            }
            
            // Filter based on scope preferences
            val shouldInclude = when {
                // Skip test configurations if not included
                configName.contains("test") -> {
                    logger.debug("  Config '${config.name}' is test - include: ${extension.includeTestDependencies}")
                    extension.includeTestDependencies
                }
                
                // Check for debug configurations
                configName.contains("debug") -> {
                    logger.debug("  Config '${config.name}' is debug - include: ${extension.includeDebugDependencies}")
                    extension.includeDebugDependencies
                }
                
                // Check for release configurations
                configName.contains("release") -> {
                    logger.debug("  Config '${config.name}' is release - include: ${extension.includeReleaseDependencies}")
                    extension.includeReleaseDependencies
                }
                
                // Include non-debug/non-release/non-test configurations by default
                else -> {
                    logger.debug("  Config '${config.name}' included by default")
                    true
                }
            }
            
            if (shouldInclude) {
                configs.add(config)
                includedConfigs++
                logger.lifecycle("  Including configuration: ${config.name} from ${proj.path}")
            }
        }
        
        logger.lifecycle("Config summary for ${proj.path}: ${totalConfigs} total, ${resolvableConfigs} resolvable, ${matchedConfigs} matched target, ${includedConfigs} included")
        
        return configs
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
        
        // Write Markdown format for human readability
        val markdownFile = File(outputDirectory, "sbom-$targetName-aggregate.md")
        val markdownContent = MarkdownBomGenerator.generate(bom)
        markdownFile.writeText(markdownContent)
        logger.lifecycle("Generated aggregate SBOM: ${markdownFile.absolutePath}")
    }
}
