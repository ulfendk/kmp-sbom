# kmp-sbom

Software Bill of Materials (SBOM) generation for Kotlin Multiplatform (KMP) Gradle builds.

This Gradle plugin generates FDA-approved SBOM files in CycloneDX format for Kotlin Multiplatform projects, including dependency analysis, license detection, and vulnerability scanning.

## Features

- ✅ **FDA-Compliant SBOMs**: Generates SBOMs in CycloneDX format (JSON and XML), recognized by the FDA for medical device software submissions
- ✅ **Multi-Platform Support**: Generates separate SBOMs for each KMP target (Android, iOS, JVM, JS, etc.)
- ✅ **Aggregate SBOMs for Monorepos**: Generates comprehensive SBOMs for Android/iOS apps including all module dependencies
- ✅ **Swift Package Manager Support**: Includes iOS dependencies from Swift Package Manager (SPM) via Package.resolved files
- ✅ **Comprehensive Dependency Analysis**: Captures both direct and transitive dependencies with full dependency graphs
- ✅ **Dependency Scope Filtering**: Control which dependencies to include (debug/release/test) in aggregate SBOMs
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
    
    // Aggregate SBOM configuration (optional)
    // Configure Android app module for aggregate SBOM generation
    androidAppModule = ":androidApp"
    
    // Configure iOS framework module for aggregate SBOM generation
    iosFrameworkModule = ":shared"
    
    // Dependency scope filtering for aggregate SBOMs
    includeDebugDependencies = false      // default: false
    includeReleaseDependencies = true     // default: true
    includeTestDependencies = false       // default: false
    
    // Build breaking on violations (optional)
    // List of allowed SPDX license identifiers
    allowedLicenses = listOf("Apache-2.0", "MIT", "BSD-3-Clause")
    
    // Maximum allowed vulnerability severity (CRITICAL, HIGH, MEDIUM, LOW, NONE)
    allowedVulnerabilitySeverity = "MEDIUM"  // default: NONE
    
    // When to fail the build on violations (ALWAYS, PULL_REQUEST, NEVER)
    failOnViolation = "PULL_REQUEST"  // default: NEVER
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

### Generate Aggregate SBOM

For monorepo projects with multiple modules, you can generate aggregate SBOMs that include dependencies from all modules:

**For Android:**
```bash
./gradlew generateAndroidAggregateSbom
```

**For iOS:**
```bash
./gradlew generateIosAggregateSbom
```

These tasks require configuration in the `kmpSbom` extension (see Configuration section above).

The aggregate SBOM will include:
- All dependencies from the specified app/framework module
- All dependencies from transitive project dependencies
- Filtering based on debug/release/test scope configuration

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
    ├── iosarm64/
    │   ├── sbom-iosarm64.json
    │   └── sbom-iosarm64.xml
    └── aggregate/
        ├── sbom-android-aggregate.json
        ├── sbom-android-aggregate.xml
        ├── sbom-ios-aggregate.json
        └── sbom-ios-aggregate.xml
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

## Build Breaking on Violations

The plugin can be configured to break the build when license requirements are violated or when vulnerabilities of a certain severity are detected. This is especially useful in CI/CD pipelines like Azure Pipelines.

### Configuration

Configure build-breaking behavior in your `build.gradle.kts`:

```kotlin
kmpSbom {
    // Define allowed licenses (SPDX identifiers)
    allowedLicenses = listOf("Apache-2.0", "MIT", "BSD-3-Clause")
    
    // Set maximum allowed vulnerability severity
    // Valid values: CRITICAL, HIGH, MEDIUM, LOW, NONE (default)
    allowedVulnerabilitySeverity = "MEDIUM"
    
    // Define when to fail the build
    // ALWAYS: Always fail on violations
    // PULL_REQUEST: Only fail when running in Azure Pipelines pull requests
    // NEVER: Never fail, only report violations (default)
    failOnViolation = "PULL_REQUEST"
}
```

### License Validation

When `allowedLicenses` is configured with a non-empty list, the plugin will:
- Check every dependency's license against the allowed list
- Report components with missing license information
- Report components with licenses not in the allowed list
- Fail the build (based on `failOnViolation` setting) if violations are found

**Example:**
```kotlin
kmpSbom {
    allowedLicenses = listOf("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")
    failOnViolation = "ALWAYS"
}
```

### Vulnerability Validation

When `allowedVulnerabilitySeverity` is set to a value other than `NONE`, the plugin will:
- Scan dependencies for known vulnerabilities (requires `enableVulnerabilityScanning = true`)
- Check vulnerability severity against the allowed level
- Report any vulnerabilities more severe than the allowed level
- Fail the build (based on `failOnViolation` setting) if violations are found

Severity levels (from most to least severe):
1. **CRITICAL**: Allows CRITICAL vulnerabilities. Blocks none (CRITICAL is the highest level).
2. **HIGH**: Allows HIGH and CRITICAL vulnerabilities. Blocks none above HIGH.
3. **MEDIUM**: Allows MEDIUM, HIGH, and CRITICAL vulnerabilities. Blocks only CRITICAL and HIGH.
4. **LOW**: Allows LOW, MEDIUM, HIGH, and CRITICAL vulnerabilities. Blocks MEDIUM and above.
5. **NONE**: No vulnerabilities are allowed. Blocks all severity levels.

**Note:** The setting represents the *maximum* allowed severity. Vulnerabilities *more severe* than this level will fail the build.

**Example:**
```kotlin
kmpSbom {
    enableVulnerabilityScanning = true
    allowedVulnerabilitySeverity = "MEDIUM"
    failOnViolation = "ALWAYS"
}
```

### Azure Pipelines Integration

The `PULL_REQUEST` option is specifically designed for Azure Pipelines:
- Automatically detects if running in Azure Pipelines (via `TF_BUILD` or `AGENT_ID` environment variables)
- Checks if the build is for a pull request (via `BUILD_REASON` environment variable)
- Only fails the build when both conditions are true

This allows you to enforce strict validation on pull requests while allowing more flexibility in other build scenarios.

**Example Azure Pipelines configuration:**
```yaml
trigger:
  - main

pr:
  - '*'

pool:
  vmImage: 'ubuntu-latest'

steps:
  - task: Gradle@2
    inputs:
      workingDirectory: ''
      gradleWrapperFile: 'gradlew'
      tasks: 'generateSbom'
      options: '--info'
```

With this configuration and `failOnViolation = "PULL_REQUEST"`, the build will fail on pull requests if any license or vulnerability violations are detected.

## Aggregate SBOM for Monorepos

For monorepo projects with multiple modules, the plugin supports generating aggregate SBOMs that include dependencies from all transitive modules. This is especially useful for:

- Android apps that depend on multiple library modules
- iOS frameworks that export functionality from multiple modules
- Projects where you need a complete dependency bill of materials for a specific target

### Configuration

Configure aggregate SBOM generation in your root project's `build.gradle.kts`:

```kotlin
kmpSbom {
    // Specify the Android app module
    androidAppModule = ":androidApp"
    
    // Specify the iOS framework module
    iosFrameworkModule = ":shared"
    
    // Control which dependency scopes to include
    includeDebugDependencies = false      // Exclude debug dependencies
    includeReleaseDependencies = true     // Include release dependencies
    includeTestDependencies = false       // Exclude test dependencies
}
```

### Generating Aggregate SBOMs

Once configured, you can generate aggregate SBOMs using:

**For Android:**
```bash
./gradlew generateAndroidAggregateSbom
```

**For iOS:**
```bash
./gradlew generateIosAggregateSbom
```

### What's Included

The aggregate SBOM includes:
- All dependencies from the specified module (e.g., `:androidApp` or `:shared`)
- All dependencies from transitive project dependencies (modules that your module depends on)
- Swift Package Manager dependencies (for iOS targets, if configured)
- Filtering based on debug/release/test scope configuration

### Example Monorepo Structure

```
my-kmp-project/
├── androidApp/              # Android application module
│   └── build.gradle.kts     # Depends on :shared
├── iosApp/                  # iOS Xcode project
│   └── Package.resolved     # Swift dependencies
├── shared/                  # Shared KMP library
│   └── build.gradle.kts     # Depends on other libraries
├── core/                    # Core library module
│   └── build.gradle.kts
└── build.gradle.kts         # Root project with kmpSbom config
```

In this example:
- Setting `androidAppModule = ":androidApp"` will include dependencies from `:androidApp`, `:shared`, and `:core`
- Setting `iosFrameworkModule = ":shared"` will include dependencies from `:shared` and `:core`, plus Swift packages

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
