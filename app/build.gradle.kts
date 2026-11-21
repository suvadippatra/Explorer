plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.troikoss.continuum_explorer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.troikoss.continuum_explorer"
        minSdk = 26
        targetSdk = 36
        val propVersionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
        val propVersionName = (project.findProperty("versionName") as? String)?.takeIf { it.isNotBlank() } ?: "0.0.1"
        versionCode = propVersionCode
        versionName = propVersionName

        val abiTarget = (project.findProperty("abiTarget") as? String)?.trim().orEmpty()
        if (abiTarget.isNotEmpty() && !abiTarget.equals("universal", ignoreCase = true)) {
            ndk {
                abiFilters.add(abiTarget)
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Do not remove or modify these signingConfigs. They are necessary for building, installing,
    // and updating Android apps in AI Studio.
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
            storeFile = file(keystorePath)
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isCrunchPngs = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
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
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/versions/**"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

configurations.all {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
}

dependencies {
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.print:print:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")
    implementation("io.coil-kt:coil-svg:2.6.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.github.Rosemoe.sora-editor:editor:0.23.4")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.burhanrashid52:photoeditor:3.0.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3")
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.3")
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("net.lingala.zip4j:zip4j:2.11.6")

    // Ktor Server (Netty, WebSockets, HTML DSL)
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")

    // Apache FtpServer
    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.security.crypto)
    implementation(libs.commons.net)
    implementation(libs.jsch)
    implementation(libs.dav4jvm) {
        exclude(group = "org.ogce", module = "xpp3")
    }
    implementation(libs.okhttp)
    implementation("com.hierynomus:smbj:0.13.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}