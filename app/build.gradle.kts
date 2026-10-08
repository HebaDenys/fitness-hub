plugins {
 id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose")
 id("com.google.devtools.ksp"); id("com.google.dagger.hilt.android")
}
android {
 namespace="io.github.hebadenys.fitnesshub"; compileSdk=36
 defaultConfig { applicationId="io.github.hebadenys.fitnesshub"; minSdk=28; targetSdk=35; versionCode=10; versionName="0.3.8"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose=true }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
 }
 testOptions {
    unitTests.all {
        it.useJUnitPlatform()
        it.systemProperty("fitnesshub.schemas", "$projectDir/schemas")
        it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        it.maxHeapSize = "2g"
        it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    unitTests.isReturnDefaultValues = true
    unitTests.isIncludeAndroidResources = true
 }
 val ciKeystorePath = System.getenv("FITNESS_HUB_TEST_KEYSTORE")
 if (!ciKeystorePath.isNullOrBlank()) {
    signingConfigs {
      create("ciDebug") {
        storeFile = file(ciKeystorePath)
        storePassword = System.getenv("FITNESS_HUB_TEST_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("FITNESS_HUB_TEST_KEY_ALIAS")
        keyPassword = System.getenv("FITNESS_HUB_TEST_KEY_PASSWORD")
      }
    }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("ciDebug") } }
 }
}
dependencies {
  implementation(platform("androidx.compose:compose-bom:2025.10.01"))
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.activity:activity-compose:1.11.0")
  implementation("androidx.compose.material3:material3"); implementation("androidx.compose.material:material-icons-core"); implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); debugImplementation("androidx.compose.ui:ui-tooling")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3"); implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
  implementation("androidx.navigation:navigation-compose:2.9.3")
  implementation("androidx.room:room-runtime:2.7.2"); implementation("androidx.room:room-ktx:2.7.2"); ksp("androidx.room:room-compiler:2.7.2")
  implementation("androidx.work:work-runtime-ktx:2.10.3")
  implementation("androidx.health.connect:connect-client:1.1.0")
  implementation("com.google.dagger:hilt-android:2.57.2"); ksp("com.google.dagger:hilt-compiler:2.57.2")
  implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
  implementation("androidx.datastore:datastore-preferences:1.1.1")
  implementation("androidx.hilt:hilt-work:1.3.0"); ksp("androidx.hilt:hilt-compiler:1.3.0")
  implementation("androidx.camera:camera-core:1.4.2"); implementation("androidx.camera:camera-camera2:1.4.2"); implementation("androidx.camera:camera-lifecycle:1.4.2"); implementation("androidx.camera:camera-view:1.4.2")
  implementation("com.google.mlkit:barcode-scanning:17.3.0"); implementation("com.google.mlkit:text-recognition:16.0.1")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.4"); testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0"); testImplementation("org.mockito:mockito-core:5.14.2"); testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
  testImplementation("org.bouncycastle:bcprov-jdk18on:1.80")
  testImplementation("org.json:json:20250107")
  testImplementation("junit:junit:4.13.2")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
  testImplementation("org.robolectric:robolectric:4.16.1")
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
ksp { arg("room.schemaLocation","$projectDir/schemas") }
