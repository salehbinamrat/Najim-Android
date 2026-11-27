plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.najim.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.najim.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
    }
    flavorDimensions += listOf("edition", "arch")
    productFlavors {
        create("full") { dimension = "edition" }
        create("lite") { dimension = "edition" }
        create("arm32") {
            dimension = "arch"
            ndk { abiFilters += "armeabi-v7a" }
        }
        create("arm64") {
            dimension = "arch"
            ndk { abiFilters += "arm64-v8a" }
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("org.jsoup:jsoup:1.24.1")
    add("fullImplementation", "com.google.mlkit:text-recognition:16.0.1")
    add("liteImplementation", "com.google.android.gms:play-services-mlkit-text-recognition:19.0.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    val cameraVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    testImplementation("junit:junit:4.13.2")
}


// CI-only inventory: selected module versions, including transitive dependencies.
// Does not add any library or change packaged application code.
tasks.register("writeDependencyInventory") {
    val report = layout.buildDirectory.file("reports/dependency-audit/components.tsv")
    outputs.file(report)
    outputs.upToDateWhen { false }
    doLast {
        val components = sortedSetOf<String>()
        val variants = listOf("fullArm32", "fullArm64", "liteArm32", "liteArm64")
        val names = variants.flatMap { variant ->
            listOf(variant + "DebugRuntimeClasspath", variant + "ReleaseRuntimeClasspath",
                variant + "DebugUnitTestRuntimeClasspath")
        }
        for (name in names) {
            val graph = configurations.getByName(name).incoming.resolutionResult
            check(graph.allDependencies.none {
                it is org.gradle.api.artifacts.result.UnresolvedDependencyResult
            }) { "Unresolved dependencies in " + name }
            graph.allComponents.forEach { component ->
                val id = component.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
                if (id != null) components.add(id.group + ":" + id.module + "\t" + id.version)
            }
        }
        check(components.isNotEmpty()) { "Empty dependency inventory" }
        val output = report.get().asFile
        output.parentFile.mkdirs()
        output.writeText(components.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}
