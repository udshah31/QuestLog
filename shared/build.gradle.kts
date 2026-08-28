plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    jvmToolchain(21)

    // Android target — AGP 9 KMP library plugin (single-variant)
    androidLibrary {
        namespace = "com.questlog.shared"
        compileSdk = 36
        minSdk = 26
    }

    // JVM target used exclusively for running commonTest on the JVM (fast, no emulator needed)
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
        }

        // commonTest automatically applies to both androidUnitTest and desktopTest
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.koin.test)
        }

        // Room's MigrationTestHelper runs migration tests as plain JVM unit tests here.
        val desktopTest by getting {
            dependencies {
                implementation(libs.androidx.room.testing)
            }
        }
    }
}

// Point MigrationTestHelper at the exported schema JSONs.
tasks.withType<Test>().configureEach {
    systemProperty("questlog.schemasDir", layout.projectDirectory.dir("schemas").asFile.absolutePath)
}

// Room: output schema JSON files for migration tracking
room {
    schemaDirectory("$projectDir/schemas")
}

// KSP annotation processing for Room on Android and Desktop targets
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}
