package com.github.ulfendk.kmpsbom

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin that generates Software Bill of Materials (SBOM) for Kotlin Multiplatform projects.
 * 
 * This plugin creates SBOM files in CycloneDX format (FDA-approved) for each target platform,
 * including dependency information, licenses, and known vulnerabilities (CVEs).
 */
class KmpSbomPlugin : Plugin<Project> {
    
    override fun apply(project: Project) {
        // Create extension for configuration
        project.extensions.create("kmpSbom", KmpSbomExtension::class.java)
        
        // Register the SBOM generation task
        project.tasks.register("generateSbom", GenerateSbomTask::class.java).configure {
            group = "verification"
            description = "Generates SBOM files for all Kotlin Multiplatform targets"
            outputDir.set(project.layout.buildDirectory.dir("sbom"))
        }
        
        // Also register per-target tasks after project evaluation
        project.afterEvaluate {
            registerPerTargetTasks(project)
            registerAggregateTasks(project)
        }
    }
    
    private fun registerPerTargetTasks(project: Project) {
        // Check if this is a Kotlin Multiplatform project
        project.extensions.findByName("kotlin")?.let { kotlinExt ->
            try {
                val kotlinExtClass = kotlinExt.javaClass
                val targetsMethod = kotlinExtClass.getMethod("getTargets")
                @Suppress("UNCHECKED_CAST")
                val targets = targetsMethod.invoke(kotlinExt) as? org.gradle.api.NamedDomainObjectContainer<*>
                
                targets?.forEach { target ->
                    val targetName = (target as org.gradle.api.Named).name
                    project.tasks.register("generateSbom${targetName.capitalize()}", GenerateSbomTask::class.java).configure {
                        group = "verification"
                        description = "Generates SBOM for $targetName target"
                        targetPlatform.set(targetName)
                        outputDir.set(project.layout.buildDirectory.dir("sbom/$targetName"))
                    }
                }
            } catch (e: Exception) {
                project.logger.warn("Could not register per-target SBOM tasks: ${e.message}")
            }
        }
    }
    
    private fun registerAggregateTasks(project: Project) {
        val extension = project.extensions.getByType(KmpSbomExtension::class.java)
        
        // Register aggregate Android SBOM task if configured
        if (extension.androidAppModule != null) {
            project.tasks.register("generateAndroidAggregateSbom", GenerateAggregateSbomTask::class.java).configure {
                group = "verification"
                description = "Generates aggregate SBOM for Android app including all module dependencies"
                targetPlatform.set("android")
                moduleProject.set(extension.androidAppModule)
                outputDir.set(project.layout.buildDirectory.dir("sbom/aggregate"))
            }
        }
        
        // Register aggregate iOS SBOM task if configured
        if (extension.iosFrameworkModule != null) {
            project.tasks.register("generateIosAggregateSbom", GenerateAggregateSbomTask::class.java).configure {
                group = "verification"
                description = "Generates aggregate SBOM for iOS framework including all module dependencies"
                targetPlatform.set("ios")
                moduleProject.set(extension.iosFrameworkModule)
                outputDir.set(project.layout.buildDirectory.dir("sbom/aggregate"))
            }
        }
    }
}

private fun String.capitalize(): String = 
    this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
