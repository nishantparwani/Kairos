plugins {
 id("com.android.application") version "9.0.0" apply false
 // Not applied: AGP 9 compiles Kotlin itself. Kept so Kotlin 2.3 (needed to run on Java 25) is on the classpath.
 id("org.jetbrains.kotlin.android") version "2.3.20" apply false
 id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
 id("com.google.gms.google-services") version "4.5.0" apply false
}
