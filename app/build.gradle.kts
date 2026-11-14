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
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val keystorePath =
                System.getenv("KEYSTORE_FILE")
                    ?: localProps.getProperty("signing.storeFile")
                    ?: "release.keystore"
            val keystoreFile = file(keystorePath)
            val storePass = System.getenv("KEYSTORE_PASSWORD") ?: localProps.getProperty("signing.storePassword")
            val alias = System.getenv("KEY_ALIAS") ?: localProps.getProperty("signing.keyAlias")
            val keyPass = System.getenv("KEY_PASSWORD") ?: localProps.getProperty("signing.keyPassword")
            if (keystoreFile.exists() && storePass != null && alias != null && keyPass != null) {
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
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
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
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
