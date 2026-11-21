plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "es.thormando.motionpad"
    buildFeatures { buildConfig = true }
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "es.thormando.motionpad"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "27.0"
    }
    buildTypes { getByName("debug") { applicationIdSuffix = ".debug" } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    kotlinOptions { jvmTarget = "1.8" }
}
dependencies {
    implementation("com.facebook.react:react-android:0.76.9")
    implementation("com.facebook.react:hermes-android:0.76.9")
    testImplementation("junit:junit:4.13.2")
}
val bundleRoot = layout.buildDirectory.dir("generated/motionpad")
val bundleJs by tasks.registering(Exec::class) {
    val projectRoot = rootProject.projectDir.parentFile
    workingDir(projectRoot)
    inputs.files(fileTree(projectRoot.resolve("src")),projectRoot.resolve("index.js"),projectRoot.resolve("package-lock.json"))
    outputs.dir(bundleRoot)
    val output = bundleRoot.get().asFile
    doFirst { output.resolve("assets").mkdirs(); output.resolve("res").mkdirs() }
    commandLine("node","node_modules/react-native/cli.js","bundle","--platform","android","--dev","false",
        "--entry-file","index.js","--bundle-output",output.resolve("assets/index.android.bundle"),"--assets-dest",output.resolve("res"))
}
android.sourceSets["main"].assets.srcDir(bundleRoot.map { it.dir("assets") })
android.sourceSets["main"].res.srcDir(bundleRoot.map { it.dir("res") })
tasks.named("preBuild") { dependsOn(bundleJs) }
