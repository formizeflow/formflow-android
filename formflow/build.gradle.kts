plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.formflow"
    compileSdk = 34
    defaultConfig { minSdk = 23; consumerProguardFiles("consumer-rules.pro") }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    publishing { singleVariant("release") }
}

dependencies {
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // the JVM stub of android.org.json does not parse
}

afterEvaluate {
    publishing { publications { create<MavenPublication>("release") { from(components["release"]); groupId = "io.github.formizeflow"; artifactId = "formflow-android"; version = "0.1.0" } } }
}
