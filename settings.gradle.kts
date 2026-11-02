pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroupByRegex("com\\.github\\.holepunchto\\..*") }
        }
        maven { url = uri(providers.gradleProperty("qvacMavenDir").orElse("vendor/qvac").get()) }
    }
}
rootProject.name = "ArgumentCat"
include(":core", ":app")
