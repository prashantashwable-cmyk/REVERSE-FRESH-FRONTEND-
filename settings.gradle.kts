pluginManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralGoogleMirror" }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralGoogleMirror" }
        mavenCentral()
    }
}
rootProject.name = "AIEC-Elevator-OS"
include(":app")
