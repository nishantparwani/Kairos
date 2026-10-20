plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.plugin.compose")
}
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")
android {
 namespace = "com.kairos.campus"
 compileSdk = 35
 defaultConfig { applicationId = "com.kairos.campus"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
 flavorDimensions += "mode"
 productFlavors {
  create("demo") { dimension = "mode"; applicationIdSuffix = ".demo"; buildConfigField("boolean", "DEMO", "true") }
  create("live") { dimension = "mode"; buildConfigField("boolean", "DEMO", "false") }
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.04.01"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
 implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
 implementation("com.google.firebase:firebase-auth")
 implementation("com.google.firebase:firebase-firestore")
 implementation("com.google.firebase:firebase-functions")
 implementation("com.google.firebase:firebase-messaging")
 implementation("com.google.firebase:firebase-storage")
 implementation("com.google.code.gson:gson:2.12.1")
 implementation("com.razorpay:checkout:1.6.40")
 implementation("io.coil-kt:coil-compose:2.7.0")
 testImplementation("junit:junit:4.13.2")
}

// Keep the no-account demo usable even after a live Firebase config is added.
tasks.configureEach {
 if (name.startsWith("processDemo") && name.endsWith("GoogleServices")) enabled = false
}
