plugins {
 id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.kapt"); id("com.google.dagger.hilt.android")
}
android {
 namespace="com.openhealthhub.app"; compileSdk=36
 defaultConfig { applicationId="com.openhealthhub.app"; minSdk=28; targetSdk=35; versionCode=1; versionName="0.2.0"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose=true }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.08.01"))
 implementation("androidx.core:core-ktx:1.17.0")
 implementation("androidx.activity:activity-compose:1.11.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3"); implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
 implementation("androidx.navigation:navigation-compose:2.9.3")
 implementation("androidx.room:room-runtime:2.7.2"); implementation("androidx.room:room-ktx:2.7.2"); kapt("androidx.room:room-compiler:2.7.2")
 implementation("androidx.work:work-runtime-ktx:2.10.3")
 implementation("androidx.health.connect:connect-client:1.1.0")
 implementation("com.google.dagger:hilt-android:2.57.2"); kapt("com.google.dagger:hilt-compiler:2.57.2")
 testImplementation("junit:junit:4.13.2"); testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
kapt { correctErrorTypes=true }
