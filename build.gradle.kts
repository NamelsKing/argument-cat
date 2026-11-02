import java.util.Properties

plugins {
    id("com.android.application") version "9.3.0" apply false
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}

// Keep generated files outside the source folder when configured locally.
val localSettings = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val externalBuildRoot = providers.gradleProperty("argumentCatBuildDir").orNull
    ?: localSettings.getProperty("argumentCatBuildDir")
if (externalBuildRoot != null) {
    subprojects { layout.buildDirectory.set(file("$externalBuildRoot/$name")) }
}
