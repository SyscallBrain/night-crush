plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "pt.tiago.nightcrush"
    // API 37.1: matches the reference games in the series and the CI SDK mirror.
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "pt.tiago.nightcrush"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Release signing is read from the developer's ~/.gradle/gradle.properties and never committed.
    // Without these properties (CI) the release build is produced unsigned.
    val releaseStoreFile = providers.gradleProperty("nightCrush.release.storeFile").orNull
    signingConfigs {
        if (releaseStoreFile != null) create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = providers.gradleProperty("nightCrush.release.storePassword").get()
            keyAlias = providers.gradleProperty("nightCrush.release.keyAlias").get()
            keyPassword = providers.gradleProperty("nightCrush.release.keyPassword").get()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            // Keep the git revision out of the APK so release builds are reproducible from source.
            vcsInfo.include = false
        }
    }
    // The dependency metadata block is encrypted for Google Play only; F-Droid rejects it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    sourceSets["main"].assets.directories.add(layout.buildDirectory.dir("generated/nightAssets").get().asFile.path)
}
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.datastore)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
    // Screenshot tests render the real screens on the JVM (no emulator); see ScreenshotTest.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
// Screenshots are opt-in: ./gradlew :app:testDebugUnitTest -PncShots --tests '*ScreenshotTest'
tasks.withType<Test>().configureEach {
    systemProperty("nc.shots", providers.gradleProperty("ncShots").isPresent.toString())
}
// The packaged catalog is produced and verified by :leveltool; wire it as soon as
// content exists so app-only builds still work during content development.
if (rootProject.file("content/catalog.json").exists()) {
    tasks.named("preBuild") { dependsOn(":leveltool:exportGameAssets") }
}
