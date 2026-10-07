import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import java.util.Properties

plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so org.jetbrains.kotlin.android is not applied.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.play.publisher)
}

// Release builds are signed with the Play upload key described in keystore.properties (gitignored).
// Without that file, release builds come out unsigned and Play rejects them.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// Default Ethereum mainnet RPC, overridable per machine with `ethereum.rpc.url` in local.properties
// (keeps provider API keys out of git). Users can also add their own RPCs in Settings at runtime.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val mainnetRpcUrl: String =
    localProperties.getProperty("ethereum.rpc.url") ?: "https://ethereum-rpc.publicnode.com"

android {
    namespace = "com.ethnym"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ethnym"
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MAINNET_RPC_URL", "\"$mainnetRpcUrl\"")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
            // Licence and build metadata that several web3j dependency jars all ship.
            excludes += setOf(
                "META-INF/DISCLAIMER",
                "META-INF/INDEX.LIST",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/FastDoubleParser-*",
            )
        }
    }
}

// Gradle Play Publisher: `./gradlew publishReleaseBundle` builds the release bundle and uploads it to
// the internal testing track, with the release notes in src/main/play/release-notes.
// It authenticates with the Play service-account key in zxstimlabs-play-service-account.json
// (gitignored), or, without that file, with the key's JSON contents in ANDROID_PUBLISHER_CREDENTIALS.
play {
    val credentials = rootProject.file("zxstimlabs-play-service-account.json")
    if (credentials.exists()) serviceAccountCredentials.set(credentials)
    track.set("internal")
    defaultToAppBundles.set(true)
    // Play rejects a reused versionCode, so each upload gets one above the highest already on Play.
    // AUTO asks Play for that number while building, so it is only on for publish runs: plain
    // bundleRelease/assembleRelease builds stay offline and use the versionCode in defaultConfig.
    val publishing = gradle.startParameter.taskNames.any { it.substringAfterLast(':').startsWith("publish") }
    if (publishing) resolutionStrategy.set(ResolutionStrategy.AUTO)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.mlkit.vision)
    // Bundled QR model: scanning works without Google Play services.
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    // web3j: mnemonics, HD derivation, signing, transactions, ABI, JSON-RPC and ENS.
    // The excluded modules are transports and signers this app never uses, two of them desktop-native.
    implementation(libs.web3j.core) {
        exclude(group = "com.github.jnr") // Unix-socket IPC transport (native)
        exclude(group = "org.java-websocket") // WebSocket transport
        exclude(group = "software.amazon.awssdk") // AWS KMS signer
        exclude(group = "io.consensys.protocols") // jc-kzg-4844 blob library (native)
        exclude(group = "org.connid") // identity-connector framework that tuweni-bytes declares but never needs
    }
    // Key derivation (PBKDF2, scrypt) for the keystore and backup file formats.
    implementation(libs.bouncycastle.bcprov)
    // Draws the receive-address QR code.
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
