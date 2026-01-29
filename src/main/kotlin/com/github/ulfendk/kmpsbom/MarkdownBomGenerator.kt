package com.github.ulfendk.kmpsbom

import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.vulnerability.Vulnerability
import java.text.SimpleDateFormat

/**
 * Generates a Markdown representation of a CycloneDX BOM
 */
object MarkdownBomGenerator {
    
    fun generate(bom: Bom): String {
        val builder = StringBuilder()
        
        // Title
        builder.append("# Software Bill of Materials (SBOM)\n\n")
        
        // Metadata section
        appendMetadata(builder, bom)
        
        // Components section
        appendComponents(builder, bom)
        
        // Vulnerabilities section (if any)
        appendVulnerabilities(builder, bom)
        
        return builder.toString()
    }
    
    private fun appendMetadata(builder: StringBuilder, bom: Bom) {
        builder.append("## Metadata\n\n")
        
        if (bom.serialNumber != null) {
            builder.append("- **Serial Number**: `${bom.serialNumber}`\n")
        }
        
        if (bom.version != null) {
            builder.append("- **Version**: ${bom.version}\n")
        }
        
        bom.metadata?.let { metadata ->
            if (metadata.timestamp != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
                builder.append("- **Timestamp**: ${dateFormat.format(metadata.timestamp)}\n")
            }
            
            metadata.component?.let { component ->
                builder.append("- **Component**: ${component.name}")
                if (component.version != null) {
                    builder.append(" v${component.version}")
                }
                builder.append("\n")
                
                if (component.description != null) {
                    builder.append("- **Description**: ${component.description}\n")
                }
            }
            
            if (metadata.authors != null && metadata.authors.isNotEmpty()) {
                val authors = metadata.authors.joinToString(", ") { it.name ?: "Unknown" }
                builder.append("- **Authors**: $authors\n")
            }
        }
        
        builder.append("\n")
    }
    
    private fun appendComponents(builder: StringBuilder, bom: Bom) {
        val components = bom.components
        
        if (components == null || components.isEmpty()) {
            builder.append("## Components\n\n")
            builder.append("*No components found*\n\n")
            return
        }
        
        builder.append("## Components (${components.size})\n\n")
        
        // Group components by type
        val componentsByType = components.groupBy { it.type ?: Component.Type.LIBRARY }
        
        componentsByType.forEach { (type, typeComponents) ->
            builder.append("### ${type.name.lowercase().replaceFirstChar { it.uppercase() }} (${typeComponents.size})\n\n")
            
            // Sort components by group and name
            val sortedComponents = typeComponents.sortedWith(
                compareBy({ it.group ?: "" }, { it.name ?: "" })
            )
            
            sortedComponents.forEach { component ->
                appendComponent(builder, component)
            }
            
            builder.append("\n")
        }
    }
    
    private fun appendComponent(builder: StringBuilder, component: Component) {
        builder.append("#### ")
        
        if (component.group != null) {
            builder.append("${component.group}:")
        }
        
        builder.append(component.name)
        
        if (component.version != null) {
            builder.append(" @ ${component.version}")
        }
        
        builder.append("\n\n")
        
        // PURL
        if (component.purl != null) {
            builder.append("- **PURL**: `${component.purl}`\n")
        }
        
        // License
        component.licenseChoice?.let { licenseChoice ->
            val licenses = mutableListOf<String>()
            
            licenseChoice.licenses?.forEach { license ->
                val licenseStr = if (license.id != null) {
                    license.id
                } else if (license.name != null) {
                    license.name
                } else {
                    "Unknown"
                }
                licenses.add(licenseStr)
            }
            
            if (licenses.isNotEmpty()) {
                builder.append("- **License**: ${licenses.joinToString(", ")}\n")
            }
        }
        
        // Hashes
        if (component.hashes != null && component.hashes.isNotEmpty()) {
            builder.append("- **Hashes**:\n")
            component.hashes.forEach { hash ->
                builder.append("  - ${hash.algorithm}: `${hash.value}`\n")
            }
        }
        
        builder.append("\n")
    }
    
    private fun appendVulnerabilities(builder: StringBuilder, bom: Bom) {
        val vulnerabilities = bom.vulnerabilities
        
        if (vulnerabilities == null || vulnerabilities.isEmpty()) {
            return
        }
        
        builder.append("## Vulnerabilities (${vulnerabilities.size})\n\n")
        builder.append("⚠️ **Security Alert**: This SBOM contains components with known vulnerabilities.\n\n")
        
        // Sort by severity (if available)
        val sortedVulnerabilities = vulnerabilities.sortedWith(
            compareBy<Vulnerability> { vuln ->
                // Sort by severity: CRITICAL, HIGH, MEDIUM, LOW, INFO, UNKNOWN
                when (vuln.ratings?.firstOrNull()?.severity?.name) {
                    "CRITICAL" -> 0
                    "HIGH" -> 1
                    "MEDIUM" -> 2
                    "LOW" -> 3
                    "INFO" -> 4
                    else -> 5
                }
            }.thenBy { it.id }
        )
        
        sortedVulnerabilities.forEach { vulnerability ->
            appendVulnerability(builder, vulnerability, bom.components)
        }
    }
    
    private fun appendVulnerability(
        builder: StringBuilder,
        vulnerability: Vulnerability,
        components: List<Component>?
    ) {
        builder.append("### ${vulnerability.id ?: "Unknown Vulnerability"}\n\n")
        
        // Severity
        vulnerability.ratings?.firstOrNull()?.severity?.let { severity ->
            val emoji = when (severity.name) {
                "CRITICAL" -> "🔴"
                "HIGH" -> "🟠"
                "MEDIUM" -> "🟡"
                "LOW" -> "🟢"
                else -> "⚪"
            }
            builder.append("- **Severity**: $emoji ${severity.name}\n")
        }
        
        // Description
        if (vulnerability.description != null) {
            builder.append("- **Description**: ${vulnerability.description}\n")
        }
        
        // Affected components
        if (vulnerability.affects != null && vulnerability.affects.isNotEmpty()) {
            builder.append("- **Affected Components**:\n")
            vulnerability.affects.forEach { target ->
                val ref = target.ref
                if (ref != null) {
                    // Find the component by bomRef
                    val component = components?.find { it.bomRef == ref }
                    if (component != null) {
                        builder.append("  - ${component.group}:${component.name}@${component.version}\n")
                    } else {
                        builder.append("  - $ref\n")
                    }
                }
            }
        }
        
        // Source/References
        if (vulnerability.source != null) {
            builder.append("- **Source**: ${vulnerability.source.name}\n")
            if (vulnerability.source.url != null) {
                builder.append("  - URL: ${vulnerability.source.url}\n")
            }
        }
        
        builder.append("\n")
    }
}
