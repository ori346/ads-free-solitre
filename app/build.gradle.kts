import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps =
    Properties().apply {
        val propsFile = rootProject.file("local.properties")
        if (propsFile.exists()) {
            propsFile.inputStream().use(::load)
        }
    }

android {
    namespace = "com.adfree.solitaire"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.adfree.solitaire"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0"
    }

    val hasCustomReleaseKeystore =
        run {
            val keystorePath =
                System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
                    ?: localProps.getProperty("signing.storeFile")?.takeIf { it.isNotBlank() }
                    ?: "release.keystore"
            val keystoreFile = file(keystorePath)
            val storePass =
                System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
                    ?: localProps.getProperty("signing.storePassword")?.takeIf { it.isNotBlank() }
            val alias =
                System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
                    ?: localProps.getProperty("signing.keyAlias")?.takeIf { it.isNotBlank() }
            val keyPass =
                System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                    ?: localProps.getProperty("signing.keyPassword")?.takeIf { it.isNotBlank() }
            keystoreFile.exists() && storePass != null && alias != null && keyPass != null
        }

    signingConfigs {
        if (hasCustomReleaseKeystore) {
            create("release") {
                val keystorePath =
                    System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
                        ?: localProps.getProperty("signing.storeFile")?.takeIf { it.isNotBlank() }
                        ?: "release.keystore"
                storeFile = file(keystorePath)
                storePassword =
                    System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
                        ?: localProps.getProperty("signing.storePassword")
                keyAlias =
                    System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
                        ?: localProps.getProperty("signing.keyAlias")
                keyPassword =
                    System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                        ?: localProps.getProperty("signing.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig =
                if (hasCustomReleaseKeystore) {
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

    buildFeatures {
        compose = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
