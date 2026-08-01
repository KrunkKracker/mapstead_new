import org.gradle.api.tasks.testing.Test
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jumastappworks.mapstead"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jumastappworks.mapstead"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.01"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            val fis = FileInputStream(localPropertiesFile)
            localProperties.load(fis)
            fis.close()
        }

        val maptilerApiKey = if (project.hasProperty("MAPTILER_API_KEY")) {
            project.property("MAPTILER_API_KEY") as String
        } else {
            localProperties.getProperty("MAPTILER_API_KEY") ?: ""
        }
        val maptilerConfigured = maptilerApiKey.isNotBlank()

        buildConfigField("boolean", "GOOGLE_DRIVE_BACKUP_ENABLED", "false")
        buildConfigField("String", "MAPTILER_API_KEY", "\"$maptilerApiKey\"")
        buildConfigField("boolean", "MAPTILER_CONFIGURED", "$maptilerConfigured")

        buildConfigField("String", "BASEMAP_STREET_URL", "\"https://tiles.openfreemap.org/styles/liberty\"")
        buildConfigField("String", "BASEMAP_SATELLITE_URL", "\"\"")
        buildConfigField("String", "BASEMAP_OUTDOORS_URL", "\"https://tiles.openfreemap.org/styles/fiord\"")
        buildConfigField("String", "BASEMAP_LIGHT_URL", "\"https://tiles.openfreemap.org/styles/positron\"")
        buildConfigField("String", "BASEMAP_DARK_URL", "\"https://tiles.openfreemap.org/styles/dark\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.hilt)
    implementation(libs.androidx.hilt.work)
    "ksp"(libs.hilt.compiler)
    "ksp"(libs.androidx.hilt.compiler)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.drive.api)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.client.gson)
    implementation(libs.google.http.client.gson)
    implementation(libs.play.services.auth)
    implementation(libs.google.auth.oauth2.http)
    implementation(libs.maplibre.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<Test>().configureEach {
    minHeapSize = "256m"
    maxHeapSize = "1536m"
    maxParallelForks = 1
    forkEvery = 10
    jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
}
