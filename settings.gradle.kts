// This block is for resolving plugins that your build script itself depends on.
pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        // This block is for Baritone, which is a dependency for Meteor Client, which your addon depends on.
        maven {
            name = "Cabaletta"
            url = uri("https://maven.cabaletta.io/releases")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

// This block is for resolving the dependencies of your project's modules.
dependencyResolutionManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "Cabaletta"
            url = uri("https://maven.cabaletta.io/releases")
        }
        mavenCentral()
    }
}

// This line tells Gradle the name of your project.
rootProject.name = "TabulaRasa"


