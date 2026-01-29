pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    
    // For local testing, use the plugin built from the parent project
    includeBuild("..")
}

rootProject.name = "example-project"
include("shared")

