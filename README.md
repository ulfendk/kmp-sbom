# kmp-sbom

Software Bill of Materials (SBOM) generation for Kotlin Multiplatform (KMP) Gradle builds.

This Gradle plugin generates FDA-approved SBOM files in CycloneDX format for Kotlin Multiplatform projects, including dependency analysis, license detection, and vulnerability scanning.

## Features

- ✅ **FDA-Compliant SBOMs**: Generates SBOMs in CycloneDX format (JSON and XML), recognized by the FDA for medical device software submissions
- ✅ **Multi-Platform Support**: Generates separate SBOMs for each KMP target (Android, iOS, JVM, JS, etc.)
- ✅ **Swift Package Manager Support**: Includes iOS dependencies from Swift Package Manager (SPM) via Package.resolved files
- ✅ **Comprehensive Dependency Analysis**: Captures both direct and transitive dependencies with full dependency graphs
- ✅ **License Detection**: Automatically detects and includes SPDX license information from Maven POM files
- ✅ **Vulnerability Scanning**: Framework for integrating CVE/vulnerability checking (extensible to NVD, OSS Index, Snyk, etc.)
- ✅ **SHA-256 Hashes**: Includes cryptographic hashes for all dependency artifacts
- ✅ **Package URLs (PURL)**: Generates standard package URLs for component identification

## Installation

Add the plugin to your KMP project's `build.gradle.kts`:

```kotlin
plugins {
    id("com.github.ulfendk.kmp-sbom") version "1.0.0-SNAPSHOT"
}
```

## Configuration

Configure the plugin using the `kmpSbom` extension:

```kotlin
kmpSbom {
    // Enable or disable vulnerability scanning (default: true)
    enableVulnerabilityScanning = true
    
    // Include license information in SBOM (default: true)
    includeLicenses = true
    
    // CycloneDX version (default: "1.5")
    sbomVersion = "1.5"
    
    // Organization information for SBOM metadata
    organizationName = "Your Organization"
    organizationUrl = "https://your-organization.com"
    
    // Path to Swift Package Manager Package.resolved file (optional)
    // Include this for iOS projects using Swift Package Manager dependencies
    packageResolvedPath = "path/to/Package.resolved"
}
```

## Usage

### Generate SBOM for All Targets

```bash
./gradlew generateSbom
```

This generates SBOM files for all detected KMP targets in `build/sbom/`.

### Generate SBOM for Specific Target

For a specific target (e.g., Android):

```bash
./gradlew generateSbomAndroiddebug
```

Available tasks are dynamically created based on your KMP configuration.

## Output

The plugin generates two SBOM files for each target:

- `sbom-<target>.json` - CycloneDX JSON format (primary FDA-approved format)
- `sbom-<target>.xml` - CycloneDX XML format (for compatibility)

### Example Output Structure

```
build/
└── sbom/
    ├── all/
    │   ├── sbom-all.json
    │   └── sbom-all.xml
    ├── androiddebug/
    │   ├── sbom-androiddebug.json
    │   └── sbom-androiddebug.xml
    └── iosarm64/
        ├── sbom-iosarm64.json
        └── sbom-iosarm64.xml
```

## FDA Compliance

This plugin generates SBOMs that align with FDA requirements for medical device software:

1. **Format**: CycloneDX (FDA-recognized format alongside SPDX)
2. **Required Elements** (per NTIA minimum):
   - Component names and versions
   - Supplier information
   - Unique identifiers (PURL)
   - Dependency relationships
   - Licensing information
   - Cryptographic hashes
3. **Vulnerability Information**: Framework for including CVE data
4. **Machine-Readable**: JSON/XML formats suitable for automated analysis

## SBOM Contents

Each generated SBOM includes:

- **Metadata**: Project information, timestamp, organization details
- **Components**: All dependencies with:
  - Group, name, and version
  - Package URL (PURL)
  - SHA-256 hash
  - License information (SPDX IDs)
  - Type classification (library, application, etc.)
- **Dependency Graph**: Hierarchical relationships between components
- **Vulnerabilities**: Known CVEs (when vulnerability scanning is enabled)

## Supported Platforms

The plugin automatically detects and generates SBOMs for:

- Android (Debug/Release)
- iOS (arm64, x64, Simulator arm64)
- JVM
- JavaScript (JS)
- Other KMP targets

## Swift Package Manager (SPM) Support

For iOS projects that use Swift Package Manager dependencies, you can include them in your SBOM by configuring the path to your `Package.resolved` file:

```kotlin
kmpSbom {
    packageResolvedPath = "iosApp/Package.resolved"
}
```

The plugin will automatically:
- Parse the `Package.resolved` file
- Extract all Swift package dependencies (identity, version, repository URL, revision)
- Merge them with other dependencies in the SBOM
- Generate proper Package URLs (PURL) using the `swift` type

**Note**: This feature only applies to iOS targets. When generating SBOMs for other platforms, the Swift dependencies will be excluded automatically.

### Example Package.resolved Format

The plugin supports both version 1 and version 2 formats of `Package.resolved`:

```json
{
  "pins": [
    {
      "identity": "firebase-ios-sdk",
      "kind": "remoteSourceControl",
      "location": "https://github.com/firebase/firebase-ios-sdk.git",
      "state": {
        "revision": "1cce11cf94d27e2fc194112cc7ad51e8fb279230",
        "version": "12.3.0"
      }
    }
  ]
}
```

## Vulnerability Scanning

The plugin includes a framework for vulnerability scanning. To integrate with external services:

1. **OSS Index**: Free vulnerability database by Sonatype
2. **NVD API**: NIST National Vulnerability Database
3. **GitHub Security Advisory**: GitHub's security database
4. **Snyk**: Commercial vulnerability scanning service

The `VulnerabilityScanner` class can be extended to integrate with these services.

## Development

### Building the Plugin

```bash
./gradlew build
```

### Testing

Apply the plugin to a test KMP project and run:

```bash
./gradlew generateSbom --info
```

## License Information

The plugin uses SPDX identifiers for license detection. Common licenses are automatically mapped:

- Apache-2.0, Apache-1.1
- MIT
- BSD-2-Clause, BSD-3-Clause
- GPL-2.0, GPL-3.0
- LGPL-2.1, LGPL-3.0
- EPL-1.0, EPL-2.0
- MPL-2.0
- And more...

## Requirements

- Gradle 8.0 or higher
- Kotlin 1.9.0 or higher
- Java 17 or higher

## Contributing

Contributions are welcome! Please ensure:

1. Code builds successfully
2. Documentation is updated
3. Changes are minimal and focused

## References

- [FDA Medical Device Cybersecurity Guidance](https://www.fda.gov/medical-devices/cybersecurity)
- [CycloneDX Specification](https://cyclonedx.org/)
- [NTIA SBOM Minimum Elements](https://www.ntia.gov/files/ntia/publications/sbom_minimum_elements_report.pdf)
- [SPDX License List](https://spdx.org/licenses/)

## Support

For issues, questions, or contributions, please visit the [GitHub repository](https://github.com/ulfendk/kmp-sbom).
