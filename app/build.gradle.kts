plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * Signing strategy (see README "Assinatura"):
 *  - If COCKPIT_KEYSTORE_FILE (+ password/alias env vars) is provided, e.g. decoded from a GitHub secret,
 *    both debug and release builds are signed with it.
 *  - Otherwise the repository debug keystore (keystore/cockpit-debug.jks, public, debug-only) is used so that
 *    every CI/local debug build has the same signature and can update the previous installation.
 */
val envKeystore = System.getenv("COCKPIT_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "com.bmwcockpit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bmwcockpit.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("cockpit") {
            if (envKeystore != null) {
                storeFile = file(envKeystore)
                storePassword = System.getenv("COCKPIT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("COCKPIT_KEY_ALIAS")
                keyPassword = System.getenv("COCKPIT_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("keystore/cockpit-debug.jks")
                storePassword = "cockpit-debug"
                keyAlias = "cockpit-debug"
                keyPassword = "cockpit-debug"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("cockpit")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("cockpit")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable += setOf("ObsoleteLintCustomCheck", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.datastore.preferences.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
