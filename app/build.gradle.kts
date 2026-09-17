import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.networktoolbox"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.networktoolbox"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 5
        versionName = "0.5.0"

        // Keep dependency locales from shadowing supported fallback languages.
        resourceConfigurations += listOf("en", "b+zh+Hans")

        testInstrumentationRunner = "com.networktoolbox.RecreationTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = rootProject.file(storeFilePath)
            }
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

kapt {
    correctErrorTypes = true
}

// AGP's lint source model includes Hilt's AndroidTest generated Java. Finish its
// producer before analysis, including when lint and AndroidTest assembly share
// one invocation; otherwise K2 lint can read a temporarily missing supplier file.
tasks.matching {
    it.name in setOf("lintAnalyzeDebug", "lintAnalyzeDebugUnitTest", "lintAnalyzeDebugAndroidTest")
}.configureEach {
    dependsOn("hiltJavaCompileDebugAndroidTest")
}

dependencies {
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:dns"))
    implementation(project(":feature:history"))
    implementation(project(":feature:lanscan"))
    implementation(project(":feature:traceroute"))
    implementation(project(":feature:port"))
    implementation(project(":feature:ping"))
    implementation(project(":feature:report"))
    implementation(project(":feature:subnet"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:permission"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.com.google.dagger.hilt.android)
    kapt(libs.com.google.dagger.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.com.google.dagger.hilt.android.testing)
    kaptAndroidTest(libs.com.google.dagger.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
