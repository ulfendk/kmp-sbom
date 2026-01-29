plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.github.ulfendk"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    
    // CycloneDX for SBOM generation (FDA-approved format)
    implementation("org.cyclonedx:cyclonedx-core-java:8.0.3")
    
    // For vulnerability scanning
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

gradlePlugin {
    plugins {
        create("kmpSbomPlugin") {
            id = "com.github.ulfendk.kmp-sbom"
            implementationClass = "com.github.ulfendk.kmpsbom.KmpSbomPlugin"
            displayName = "KMP SBOM Generator"
            description = "Generates Software Bill of Materials (SBOM) for Kotlin Multiplatform projects with dependency analysis and vulnerability scanning"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}
