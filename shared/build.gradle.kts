plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Upstream's exported schemas 24-32 are copied in, since Room's auto-migrations read them; Room writes 33 here,
// and AppDatabaseSchemaTest checks every file against upstream's.
room {
    schemaDirectory("$projectDir/schemas")
}

// Upstream's JSON fixtures, read in place so the ported tests see exactly what upstream's do. The iOS
// simulator runs tests as host processes, so the same absolute path works there.
val upstreamTestResources = rootProject.file("upstream/app/src/test/resources")
// The SABR fixture lives with the simulator tests that also replay it (iosAppTests' resources).
val sabrFixture = rootProject.file("iosApp/iosAppTests/Sabr")
val generateFixturePath = tasks.register("generateFixturePath") {
    val out = layout.buildDirectory.dir("generated/fixturePath/kotlin")
    val path = upstreamTestResources.absolutePath.replace('\\', '/')
    val sabrPath = sabrFixture.absolutePath.replace('\\', '/')
    val repoRoot = rootProject.projectDir.absolutePath.replace('\\', '/')
    inputs.property("path", path)
    inputs.property("sabrPath", sabrPath)
    inputs.property("repoRoot", repoRoot)
    outputs.dir(out)
    doLast {
        val file = out.get().file("io/github/aedev/flow/testing/FixturePath.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package io.github.aedev.flow.testing\n\ninternal const val UPSTREAM_TEST_RESOURCES = \"$path\"\n\n" +
                "internal const val SABR_FIXTURE_DIR = \"$sabrPath\"\n\n" +
                // The checkout itself, for tests that read the port's own sources and schemas beside upstream's.
                "internal const val REPO_ROOT = \"$repoRoot\"\n",
        )
    }
}

// Upstream's BotGuard page (assets/po_token.html), compiled in as a string so it follows the pin.
val poTokenHtml = rootProject.file("upstream/app/src/main/assets/po_token.html")
val generatePoTokenHtml = tasks.register("generatePoTokenHtml") {
    val out = layout.buildDirectory.dir("generated/poTokenHtml/kotlin")
    inputs.file(poTokenHtml)
    outputs.dir(out)
    doLast {
        val escaped = buildString {
            poTokenHtml.readText().forEach { c ->
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }
        val file = out.get().file("io/github/aedev/flow/utils/potoken/PoTokenHtml.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package io.github.aedev.flow.utils.potoken\n\ninternal const val PO_TOKEN_HTML = \"$escaped\"\n")
    }
}

// Upstream's BuildConfig.APP_LANGUAGE_TAGS: English plus every translated `values-*` folder, by upstream's own rule.
val upstreamRes = rootProject.file("upstream/app/src/main/res")
val generateAppLanguageTags = tasks.register("generateAppLanguageTags") {
    val out = layout.buildDirectory.dir("generated/appLanguageTags/kotlin")
    val tags =
        upstreamRes
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.resolve("strings.xml").exists() }
            .mapNotNull { dir ->
                val qualifier = dir.name.removePrefix("values-")
                when {
                    qualifier == dir.name -> null
                    qualifier.startsWith("b+") -> qualifier.removePrefix("b+").replace('+', '-')
                    Regex("[a-z]{2,3}(-r[A-Z]{2})?").matches(qualifier) -> qualifier.replace("-r", "-")
                    else -> null
                }
            }.map { if (it == "in" || it.startsWith("in-")) "id" + it.removePrefix("in") else it }
            .sorted()
            .let { (listOf("en") + it).joinToString(",") }
    inputs.property("tags", tags)
    outputs.dir(out)
    doLast {
        val file = out.get().file("io/github/aedev/flow/utils/AppLanguageTags.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package io.github.aedev.flow.utils\n\ninternal const val APP_LANGUAGE_TAGS = \"$tags\"\n")
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
        commonMain {
            kotlin.srcDir(generatePoTokenHtml)
            kotlin.srcDir(generateAppLanguageTags)
        }
        commonMain.dependencies {
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.atomicfu)
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
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences.core)
            implementation(libs.paging.common)
            implementation(libs.okio)
            implementation(libs.xmlutil.core)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.encoding)
            implementation(libs.icu4j)
            implementation(libs.graal.polyglot)
            implementation(libs.graal.js)
            implementation(libs.brotli.dec)
        }
        commonTest {
            kotlin.srcDir(generateFixturePath)
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.room.testing)
                implementation(libs.ktor.client.mock)
            }
        }
        jvmTest.dependencies {
            implementation(libs.mockk)
        }
    }
}

dependencies {
    listOf("kspJvm", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room.compiler) }
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
    // CI runs the live playback tests in a step of their own, so a crash there cannot stop the rest (comma-separated).
    providers.gradleProperty("excludeTests").orNull?.split(',')?.forEach { filter.excludeTestsMatching(it) }
}
