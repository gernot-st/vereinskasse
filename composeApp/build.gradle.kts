import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.application")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // Desktop-Ziel: zum Entwickeln/Testen der Oberfläche ohne Handy.
    jvm("desktop")
    // Web-App (PWA) für iPhone und Browser – läuft nach dem ersten Laden offline.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "vereinskasse"
        browser {
            commonWebpackConfig { outputFileName = "vereinskasse.js" }
        }
        binaries.executable()
    }
    // iOS kann später ergänzt werden (iosArm64(), iosSimulatorArm64()) – siehe README.

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("sh.calvin.reorderable:reorderable:2.4.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
        }
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "de.vereinskasse"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.vereinskasse"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        create("release") {
            // Wird in GitHub Actions aus Secrets befüllt (siehe .github/workflows/build.yml).
            val keystore = System.getenv("SIGNING_KEYSTORE_FILE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("SIGNING_KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

compose.desktop {
    application {
        mainClass = "de.vereinskasse.MainKt"
    }
}

// Fertige Web-App: Distribution + Service Worker mit Dateiliste und Version (für den Offline-Betrieb)
val webApp by tasks.registering {
    group = "build"
    description = "Baut die Web-App (PWA) nach build/dist/wasmJs/productionExecutable"
    dependsOn("wasmJsBrowserDistribution")
    val ordner = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    doLast {
        val dir = ordner.get().asFile
        dir.walkTopDown().filter { it.isFile && (it.name.endsWith(".map") || it.name.endsWith("LICENSE.txt")) }.forEach { it.delete() }
        val dateien = dir.walkTopDown()
            .filter { it.isFile && it.name != "sw.js" }
            .map { it.relativeTo(dir).invariantSeparatorsPath }
            .sorted()
            .toList()
        val version = System.getenv("VERSION_NAME") ?: ("dev-" + System.currentTimeMillis())
        val sw = File(dir, "sw.js")
        sw.writeText(
            sw.readText()
                .replace("__VERSION__", "vereinskasse-$version")
                .replace("/*DATEIEN*/", (listOf("./") + dateien).joinToString(", ") { "\"$it\"" })
        )
        println("Web-App: ${dateien.size} Dateien, Version $version")
    }
}
