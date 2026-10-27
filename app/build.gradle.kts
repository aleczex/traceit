import java.util.Properties
import java.io.InputStream
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.oss.licenses)
}

android {
    namespace = "com.aletchec.traceit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aletchec.traceit"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val propertiesFile = rootProject.file("local.properties")
            if (propertiesFile.exists()) {
                propertiesFile.inputStream().use<InputStream, Unit> {
                    props.load(it)
                }
            }

            val keystoreFile = props.getProperty("RELEASE_STORE_FILE")
            storeFile = if (keystoreFile != null) file(keystoreFile) else null
            storePassword = props.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = props.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = props.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    flavorDimensions += "mode"
    productFlavors {
        create("dev") {
            dimension = "mode"
            applicationIdSuffix = ".dev"
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3375251002"
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        create("prod") {
            dimension = "mode"
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-7325364868833825~8329744634"
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"ca-app-pub-7325364868833825/3310272118\"")
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-fitness:21.2.0")
    implementation("com.google.android.gms:play-services-ads:24.0.0")
    implementation(libs.osmdroid.android)
    implementation(libs.play.services.oss.licenses)
    implementation(libs.user.messaging.platform)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:rules:1.7.0")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val rootDirectory = rootDir
val selectedDeviceId = providers.gradleProperty("deviceId").orNull
    ?: providers.gradleProperty("emulator").orNull

tasks.register("fetchScreenshots") {
    group = "verification"
    description = "Pulls test screenshots from the device/emulator to project_root/screenshots"

    val targetDir = file("${rootDirectory}/screenshots")
    val selectedDevice = selectedDeviceId

    doLast {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        val adbCmd = if (androidHome != null) {
            val adbFile = File(androidHome, "platform-tools/adb")
            val adbWinFile = File(androidHome, "platform-tools/adb.exe")
            when {
                adbWinFile.exists() -> adbWinFile.absolutePath
                adbFile.exists() -> adbFile.absolutePath
                else -> "adb"
            }
        } else {
            "adb"
        }

        val devicesProcess = ProcessBuilder(adbCmd, "devices").start()
        val devicesOutput = devicesProcess.inputStream.bufferedReader().readText()
        devicesProcess.waitFor()

        val devices = devicesOutput.lines()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("\tdevice") }
            .map { it.split("\t").first().trim() }

        println("--------------------------------------------------")
        println("Connected devices/emulators (${devices.size}):")
        if (devices.isEmpty()) {
            println("  [None found]")
        } else {
            devices.forEach { dev ->
                val isSelected = dev == selectedDevice
                println("  - $dev ${if (isSelected) "[SELECTED]" else ""}")
            }
        }
        println("--------------------------------------------------")

        if (devices.isEmpty()) {
            println("No connected devices found to pull screenshots from.")
            return@doLast
        }

        val targetsToPull = if (selectedDevice != null) {
            if (devices.contains(selectedDevice)) {
                listOf(selectedDevice)
            } else {
                println("WARNING: Specified device '$selectedDevice' not found in connected devices: $devices.")
                listOf(selectedDevice)
            }
        } else {
            if (devices.size > 1) {
                println("INFO: Multiple devices detected. Pulling screenshots from all connected devices.")
                println("      Tip: Pass -PdeviceId=<serial> or -Pemulator=<serial> to pull from a specific device.")
            }
            devices
        }

        for (deviceSerial in targetsToPull) {
            println("Pulling screenshots from device: $deviceSerial -> ${targetDir.absolutePath}")
            val pullPublicProcess = ProcessBuilder(
                adbCmd,
                "-s",
                deviceSerial,
                "pull",
                "/sdcard/Pictures/screenshots/.",
                targetDir.absolutePath
            ).start()
            pullPublicProcess.waitFor()

            val pullAppProcess = ProcessBuilder(
                adbCmd,
                "-s",
                deviceSerial,
                "pull",
                "/sdcard/Android/data/com.aletchec.traceit.dev/files/screenshots/.",
                targetDir.absolutePath
            ).start()
            pullAppProcess.waitFor()
        }
    }
}

tasks.register("runTestAndCapture") {
    group = "verification"
    description = "Runs a specific instrumentation test on a selected emulator/device and captures screenshots"

    doLast {
        val testClass = providers.gradleProperty("testClass").orNull
        val selectedDevice = providers.gradleProperty("deviceId").orNull ?: providers.gradleProperty("emulator").orNull
        val outputDir = providers.gradleProperty("output").map { file(it) }.getOrElse(file("${rootDirectory}/screenshots"))
        val variant = providers.gradleProperty("variant").orNull ?: "devDebug"

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        val adbCmd = if (androidHome != null) {
            val adbFile = File(androidHome, "platform-tools/adb")
            val adbWinFile = File(androidHome, "platform-tools/adb.exe")
            when {
                adbWinFile.exists() -> adbWinFile.absolutePath
                adbFile.exists() -> adbFile.absolutePath
                else -> "adb"
            }
        } else {
            "adb"
        }

        val devicesProcess = ProcessBuilder(adbCmd, "devices").start()
        val devicesOutput = devicesProcess.inputStream.bufferedReader().readText()
        devicesProcess.waitFor()

        val devices = devicesOutput.lines()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("\tdevice") }
            .map { it.split("\t").first().trim() }

        println("--------------------------------------------------")
        println("Connected devices/emulators (${devices.size}):")
        if (devices.isEmpty()) {
            println("  [None found]")
            throw GradleException("No connected devices or emulators found.")
        } else {
            devices.forEach { dev ->
                val isSelected = dev == selectedDevice
                println("  - $dev ${if (isSelected) "[SELECTED]" else ""}")
            }
        }
        println("--------------------------------------------------")

        val targetDevice = if (selectedDevice != null) {
            if (devices.contains(selectedDevice)) {
                selectedDevice
            } else {
                throw GradleException("Specified device '$selectedDevice' not found in connected devices: $devices")
            }
        } else {
            if (devices.size > 1) {
                throw GradleException("Multiple devices found (${devices.size}): $devices. Providing a device ID (-PdeviceId=<serial>) is mandatory when multiple devices are connected.")
            }
            devices.first()
        }

        val gradlewFilename = if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "gradlew"
        val gradlewFile = File(rootDirectory, gradlewFilename)

        val capitalizeVariant = variant.replaceFirstChar { it.uppercase() }
        println("Building APKs for variant: $variant...")
        val buildProcess = ProcessBuilder(gradlewFile.absolutePath, ":app:assemble$capitalizeVariant", ":app:assemble${capitalizeVariant}AndroidTest")
            .directory(rootDirectory)
            .inheritIO()
            .start()
        val buildExitCode = buildProcess.waitFor()
        if (buildExitCode != 0) {
            throw GradleException("Failed to build APKs (exit code $buildExitCode)")
        }

        val apkBaseDir = File(rootDirectory, "app/build/outputs/apk/dev/debug")
        val appApk = File(apkBaseDir, "app-dev-debug.apk")
        val testApk = File(apkBaseDir, "app-dev-debug-androidTest.apk")

        println("Installing app and test APKs on target device: $targetDevice")
        val installApp = ProcessBuilder(adbCmd, "-s", targetDevice, "install", "-r", appApk.absolutePath).inheritIO().start()
        installApp.waitFor()

        val installTest = ProcessBuilder(adbCmd, "-s", targetDevice, "install", "-r", testApk.absolutePath).inheritIO().start()
        installTest.waitFor()

        val testCmd = mutableListOf(adbCmd, "-s", targetDevice, "shell", "am", "instrument", "-w")
        if (testClass != null) {
            println("Running test class/method: $testClass on variant: $variant (device: $targetDevice)")
            testCmd.add("-e")
            testCmd.add("class")
            testCmd.add(testClass)
        } else {
            println("Running all instrumentation tests on variant: $variant (device: $targetDevice)")
        }
        testCmd.add("com.aletchec.traceit.dev.test/androidx.test.runner.AndroidJUnitRunner")

        val testProcess = ProcessBuilder(testCmd)
            .inheritIO()
            .start()
        val exitCode = testProcess.waitFor()

        println("Test execution completed with exit code: $exitCode. Pulling screenshots from device: $targetDevice -> ${outputDir.absolutePath}")

        val pullPublicProcess = ProcessBuilder(
            adbCmd,
            "-s",
            targetDevice,
            "pull",
            "/sdcard/Pictures/screenshots/.",
            outputDir.absolutePath
        ).start()
        pullPublicProcess.waitFor()

        val pullAppProcess = ProcessBuilder(
            adbCmd,
            "-s",
            targetDevice,
            "pull",
            "/sdcard/Android/data/com.aletchec.traceit.dev/files/screenshots/.",
            outputDir.absolutePath
        ).start()
        pullAppProcess.waitFor()

        println("Screenshots successfully saved to ${outputDir.absolutePath}")
    }
}
