import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing credentials come from env vars (CI) or a gitignored keystore.properties
// (local). If neither resolves a keystore, `release` is left unsigned and still builds.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use(::load)
}
fun signingProp(env: String, key: String): String? =
    (System.getenv(env) ?: keystoreProperties.getProperty(key))?.takeIf { it.isNotBlank() }

// RevenueCat SDK key: CI passes it as the REVENUECAT_API_KEY env var (see deploy-internal.yml);
// locally it can go in keystore.properties as `revenueCatKey`. Falls back to a placeholder so
// un-configured builds still compile.
val revenueCatKey: String? = signingProp("REVENUECAT_API_KEY", "revenueCatKey")

fun buildConfigStringLiteral(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.questlog"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.questlog.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingProp("ANDROID_KEYSTORE_FILE", "storeFile")
            if (storeFilePath != null && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = signingProp("ANDROID_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingProp("ANDROID_KEY_ALIAS", "keyAlias")
                keyPassword = signingProp("ANDROID_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "REVENUECAT_API_KEY",
                buildConfigStringLiteral(revenueCatKey ?: "REPLACE_WITH_RC_SANDBOX_KEY"),
            )
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField(
                "String",
                "REVENUECAT_API_KEY",
                buildConfigStringLiteral(revenueCatKey ?: "REPLACE_WITH_RC_PROD_KEY"),
            )
            // Attach the release signing config only when a keystore was actually resolved.
            signingConfigs.getByName("release").takeIf { it.storeFile != null }?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // ── Shared KMP module ────────────────────────────────────────────────────
    implementation(project(":shared"))

    // ── Compose BOM ──────────────────────────────────────────────────────────
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // ── Core Android ─────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── Compose UI ───────────────────────────────────────────────────────────
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ── Koin DI ──────────────────────────────────────────────────────────────
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    // ── RevenueCat ───────────────────────────────────────────────────────────
    implementation(libs.revenuecat.purchases)
    implementation(libs.revenuecat.purchases.ui)

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.koin.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
