package com.github.ulfendk.kmpsbom

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses license information from Maven POM files
 */
object PomLicenseParser {
    
    fun parse(pomFile: File): LicenseInfo? {
        return try {
            val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = docBuilder.parse(pomFile)
            doc.documentElement.normalize()
            
            val licenses = doc.getElementsByTagName("license")
            if (licenses.length > 0) {
                val licenseElement = licenses.item(0)
                val children = licenseElement.childNodes
                
                var name: String? = null
                var url: String? = null
                
                for (i in 0 until children.length) {
                    val node = children.item(i)
                    when (node.nodeName) {
                        "name" -> name = node.textContent?.trim()
                        "url" -> url = node.textContent?.trim()
                    }
                }
                
                if (name != null) {
                    val spdxId = mapToSpdxId(name)
                    LicenseInfo(id = spdxId, name = name, url = url)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Maps common license names to SPDX identifiers
     */
    private fun mapToSpdxId(licenseName: String): String {
        val normalized = licenseName.lowercase().trim()
        
        return when {
            normalized.contains("apache") && normalized.contains("2.0") -> "Apache-2.0"
            normalized.contains("apache") && normalized.contains("1.1") -> "Apache-1.1"
            normalized.contains("mit") -> "MIT"
            normalized.contains("bsd") && normalized.contains("3-clause") -> "BSD-3-Clause"
            normalized.contains("bsd") && normalized.contains("2-clause") -> "BSD-2-Clause"
            normalized.contains("gpl") && normalized.contains("3.0") -> "GPL-3.0"
            normalized.contains("gpl") && normalized.contains("2.0") -> "GPL-2.0"
            normalized.contains("lgpl") && normalized.contains("3.0") -> "LGPL-3.0"
            normalized.contains("lgpl") && normalized.contains("2.1") -> "LGPL-2.1"
            normalized.contains("eclipse public license") && normalized.contains("2.0") -> "EPL-2.0"
            normalized.contains("eclipse public license") && normalized.contains("1.0") -> "EPL-1.0"
            normalized.contains("mozilla public license 2.0") -> "MPL-2.0"
            normalized.contains("cddl") -> "CDDL-1.0"
            normalized.contains("public domain") -> "CC0-1.0"
            else -> licenseName // Return original if no mapping found
        }
    }
}

data class LicenseInfo(
    val id: String,
    val name: String,
    val url: String?
)
