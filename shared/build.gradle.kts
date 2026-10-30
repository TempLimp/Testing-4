plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Upstream's JSON fixtures, read in place so the ported tests see exactly what upstream's do. The iOS
// simulator runs tests as host processes, so the same absolute path works there.
val upstreamTestResources = rootProject.file("upstream/app/src/test/resources")
val generateFixturePath = tasks.register("generateFixturePath") {
    val out = layout.buildDirectory.dir("generated/fixturePath/kotlin")
    val path = upstreamTestResources.absolutePath.replace('\\', '/')
    inputs.property("path", path)
    outputs.dir(out)
    doLast {
        val file = out.get().file("io/github/aedev/flow/testing/FixturePath.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package io.github.aedev.flow.testing\n\ninternal const val UPSTREAM_TEST_RESOURCES = \"$path\"\n")
    }
}

kotlin {
    compilerOptions {
        // Kotlin's common atomics (AtomicReference, AtomicInt) stand in for java.util.concurrent's.
        optIn.add("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        // Locale is an expect class over java.util.Locale and NSLocale.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // jvm() exists only so commonMain compiles and its tests run on the Windows dev machine; it is never shipped.
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.encoding)
            implementation(libs.icu4j)
        }
        commonTest {
            kotlin.srcDir(generateFixturePath)
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "io.github.aedev.flow.resources"
}

// CI passes the UDID of a simulator that exists on the runner's Xcode, booted beforehand; locally the
// plugin default is used. A standalone spawn runs without the simulator's trust daemon, so every TLS
// handshake in the live API test fails (NSURLError -1200); the booted device has it.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    providers.gradleProperty("iosSimulatorDevice").orNull?.let {
        device.set(it)
        standalone.set(false)
    }
}
