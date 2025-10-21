repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    // Add this block for Baritone
    maven {
        name = "Cabaletta"
        url = uri("https://maven.cabaletta.io/releases")
    }
    mavenCentral()
    gradlePluginPortal()
}
