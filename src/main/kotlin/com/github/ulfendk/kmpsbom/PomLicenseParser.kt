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
                
                // If we have a name, use it for license detection
                if (name != null && name.isNotBlank()) {
                    val spdxId = mapToSpdxId(name)
                    return LicenseInfo(id = spdxId, name = name, url = url)
                }
                
                // Fallback: Try to detect license from URL if name is missing
                if (url != null && url.isNotBlank()) {
                    val spdxIdFromUrl = detectLicenseFromUrl(url)
                    if (spdxIdFromUrl != null) {
                        return LicenseInfo(id = spdxIdFromUrl, name = spdxIdFromUrl, url = url)
                    }
                }
                
                return null
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
            normalized.contains("apache") && normalized.contains("1.0") -> "Apache-1.0"
            normalized.contains("apache license") -> "Apache-2.0"  // Default to 2.0 if version not specified
            normalized.contains("mit") -> "MIT"
            normalized.contains("bsd") && normalized.contains("3-clause") -> "BSD-3-Clause"
            normalized.contains("bsd") && normalized.contains("2-clause") -> "BSD-2-Clause"
            normalized.contains("bsd") -> "BSD-3-Clause"  // Default to 3-clause
            normalized.contains("gpl") && normalized.contains("3.0") -> "GPL-3.0-or-later"
            normalized.contains("gpl") && normalized.contains("2.0") -> "GPL-2.0-or-later"
            normalized.contains("lgpl") && normalized.contains("3.0") -> "LGPL-3.0-or-later"
            normalized.contains("lgpl") && normalized.contains("2.1") -> "LGPL-2.1-or-later"
            normalized.contains("eclipse public license") && normalized.contains("2.0") -> "EPL-2.0"
            normalized.contains("eclipse public license") && normalized.contains("1.0") -> "EPL-1.0"
            normalized.contains("eclipse public license") -> "EPL-2.0"  // Default to 2.0
            normalized.contains("mozilla public license 2.0") -> "MPL-2.0"
            normalized.contains("mozilla public license") && normalized.contains("2.0") -> "MPL-2.0"
            normalized.contains("mpl-2.0") || normalized.contains("mpl 2.0") -> "MPL-2.0"
            normalized.contains("cddl") -> "CDDL-1.0"
            normalized.contains("public domain") -> "CC0-1.0"
            normalized.contains("unlicense") -> "Unlicense"
            normalized.contains("isc license") || normalized.contains("isc") -> "ISC"
            else -> licenseName // Return original if no mapping found
        }
    }
    
    /**
     * Attempts to detect license from URL patterns
     */
    private fun detectLicenseFromUrl(url: String): String? {
        val normalized = url.lowercase().trim()
        
        return when {
            normalized.contains("apache.org/licenses/license-2.0") -> "Apache-2.0"
            normalized.contains("apache.org/licenses/license-1.1") -> "Apache-1.1"
            normalized.contains("opensource.org/licenses/mit") -> "MIT"
            normalized.contains("opensource.org/licenses/bsd-3-clause") -> "BSD-3-Clause"
            normalized.contains("opensource.org/licenses/bsd-2-clause") -> "BSD-2-Clause"
            normalized.contains("gnu.org/licenses/gpl-3.0") -> "GPL-3.0-or-later"
            normalized.contains("gnu.org/licenses/gpl-2.0") -> "GPL-2.0-or-later"
            normalized.contains("gnu.org/licenses/lgpl-3.0") -> "LGPL-3.0-or-later"
            normalized.contains("gnu.org/licenses/lgpl-2.1") -> "LGPL-2.1-or-later"
            normalized.contains("eclipse.org/legal/epl-2.0") -> "EPL-2.0"
            normalized.contains("eclipse.org/legal/epl-v20") -> "EPL-2.0"
            normalized.contains("eclipse.org/legal/epl-v10") -> "EPL-1.0"
            normalized.contains("mozilla.org/mpl/2.0") -> "MPL-2.0"
            normalized.contains("opensource.org/licenses/isc") -> "ISC"
            else -> null
        }
    }
}

data class LicenseInfo(
    val id: String,
    val name: String,
    val url: String?
)
