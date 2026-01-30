plugins {
    kotlin("multiplatform") version "1.9.22"
    id("com.github.ulfendk.kmp-sbom")
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    
    js(IR) {
        browser()
        nodejs()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation(project(":shared"))
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// Configure SBOM generation
kmpSbom {
    enableVulnerabilityScanning = true
    includeLicenses = true
    organizationName = "Example Organization"
    organizationUrl = "https://example.com"
    packageResolvedPath = "Package.resolved"
    
    // Vulnerability scanner configuration
    vulnerabilityScanners = "all"  // Options: "ossindex", "github", "all"
    
    // Optional: Configure scanner credentials (can also use environment variables)
    // ossIndexUsername = "your-username"
    // ossIndexToken = "your-token"
    // githubToken = "ghp_yourtoken"
    
    // Example aggregate SBOM configuration for a monorepo
    // This demonstrates aggregate SBOM for the main module which depends on :shared
    androidAppModule = "."  // Using root project for Android (normally would be ":androidApp")
    iosFrameworkModule = ":shared"  // Using shared module for iOS framework
    
    // Configure which dependencies to include in aggregate SBOMs
    includeDebugDependencies = false
    includeReleaseDependencies = true
    includeTestDependencies = false
    
    // Example build-breaking configuration (commented out by default)
    // Uncomment and configure to enable build-breaking on violations
    // allowedLicenses = listOf("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")
    // allowedVulnerabilitySeverity = "MEDIUM"
    // failOnViolation = "PULL_REQUEST"
}
