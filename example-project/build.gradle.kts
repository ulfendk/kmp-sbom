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
}
