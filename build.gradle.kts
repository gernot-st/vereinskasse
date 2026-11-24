plugins {
    kotlin("multiplatform") version "2.1.21" apply false
    kotlin("plugin.serialization") version "2.1.21" apply false
    kotlin("plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
    id("com.android.application") version "8.10.1" apply false
}

// Yarn nicht herunterladen, sondern das installierte verwenden (falls vorhanden)
if (System.getenv("YARN_HERUNTERLADEN") == null) {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().download = false
    }
}
