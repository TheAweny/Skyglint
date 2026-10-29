import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import groovy.json.JsonSlurper

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.21"
    kotlin("jvm") version "2.4.10"
}

version = property("mod_version")!!
group = property("maven_group")!!
base { archivesName.set(property("archives_base_name").toString()) }

repositories {
    mavenCentral()
    maven("https://repo.hypixel.net/repository/Hypixel/") { content { includeGroup("net.hypixel") } }
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
}

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.2")
    implementation("net.fabricmc:fabric-language-kotlin:1.13.13+kotlin.2.4.10")
    compileOnly("net.hypixel:mod-api:1.0.2")
    include(implementation("maven.modrinth:hypixel-mod-api:1.0.2+build.1+mc26.1")!!)
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}
kotlin { jvmToolchain(25) }
tasks.withType<JavaCompile>().configureEach { options.release.set(25) }
tasks.test {
    useJUnitPlatform()
    systemProperty("skyglint.projectRoot", layout.projectDirectory.asFile.absolutePath)
}
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
tasks.jar { from("THIRD_PARTY_NOTICES.md", "LICENSE", "COPYING", "NOTICE") }
tasks.named<Jar>("sourcesJar") { from("THIRD_PARTY_NOTICES.md", "LICENSE", "COPYING", "NOTICE") }

tasks.register<Zip>("packageRelease") {
    dependsOn(tasks.jar)
    archiveFileName.set("Skyglint-${project.version}-Minecraft-26.2.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(tasks.jar) { into("mods") }
    from(provider { configurations.runtimeClasspath.get().files.filter {
        it.name == "fabric-api-0.161.0+26.2.jar" ||
            it.name == "fabric-language-kotlin-1.13.13+kotlin.2.4.10.jar"
    } }) { into("mods") }
    from("README.md")
    from("THIRD_PARTY_NOTICES.md")
    from("LICENSE", "COPYING", "NOTICE")
}

val smoke by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}

// Keep the opt-in graphical fixtures compilable in every build without launching a client.
tasks.check { dependsOn(smoke.compileJavaTaskName) }

val verifyProductionJar by tasks.registering {
    group = "verification"
    description = "Checks the packaged mod target, bytecode level and smoke-source isolation."
    dependsOn(tasks.jar)
    inputs.file(tasks.jar.flatMap { it.archiveFile })
    inputs.property("version", project.version.toString())
    doLast {
        ZipFile(tasks.jar.get().archiveFile.get().asFile).use { zip ->
            val entries = zip.entries().asSequence().toList()
            check(entries.none { it.name.startsWith("dev/skyglint/smoke/") }) {
                "Production JAR contains isolated smoke classes"
            }
            val classes = entries.filter { it.name.endsWith(".class") }
            check(classes.isNotEmpty()) { "Production JAR contains no classes" }
            classes.forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    val header = input.readNBytes(8)
                    check(header.size == 8 && header.take(4).map { it.toInt() and 255 } == listOf(202, 254, 186, 190)) {
                        "Invalid class header: ${entry.name}"
                    }
                    val major = ((header[6].toInt() and 255) shl 8) or (header[7].toInt() and 255)
                    check(major == 69) { "Expected JVM 25 bytecode, found class major $major: ${entry.name}" }
                }
            }
            val metadataEntry = zip.getEntry("fabric.mod.json") ?: error("Missing fabric.mod.json")
            val metadata = zip.getInputStream(metadataEntry).reader(Charsets.UTF_8).use {
                JsonSlurper().parse(it) as Map<*, *>
            }
            val dependencies = metadata["depends"] as? Map<*, *> ?: error("Missing mod dependencies")
            check(metadata["id"] == "reborn" && metadata["name"] == "Skyglint") { "Packaged mod identity changed" }
            check(metadata["version"] == project.version.toString()) { "Packaged version does not match project.version" }
            check(metadata["environment"] == "client") { "Expected client-only mod" }
            check(dependencies["minecraft"] == "26.2") { "Minecraft target must be exactly 26.2" }
            check(dependencies["java"] == ">=25") { "Java requirement must remain >=25" }
            check(zip.getEntry("skyglint.mixins.json") != null) { "Missing production Mixin configuration" }
        }
    }
}
tasks.check { dependsOn(verifyProductionJar) }

loom {
    mods {
        create("reborn") { sourceSet(sourceSets.main.get()) }
    }
    if (project.hasProperty("smoke")) {
        mods { create("skyglint-smoke") { sourceSet(smoke) } }
        runs { create("smokeClient") {
            client()
            source(smoke)
            runDir("run-smoke")
            ideConfigGenerated(false)
            if (project.hasProperty("worldSmoke")) vmArg("-Dskyglint.smoke.world=true")
            if (project.hasProperty("confirmationSmoke")) vmArg("-Dskyglint.smoke.confirmationsOnly=true")
            if (project.hasProperty("slayerPetSmoke")) vmArg("-Dskyglint.smoke.slayerPetsOnly=true")
            if (project.hasProperty("fishingSmoke")) {
                vmArg("-Dskyglint.smoke.world=true")
                vmArg("-Dskyglint.smoke.fishingOnly=true")
            }
            if (project.hasProperty("winterSmoke")) {
                vmArg("-Dskyglint.smoke.world=true")
                vmArg("-Dskyglint.smoke.winterOnly=true")
            }
            if (project.hasProperty("settingsSmoke")) {
                vmArg("-Dskyglint.smoke.world=true")
                vmArg("-Dskyglint.smoke.settingsOnly=true")
            }
            if (project.hasProperty("miningClockSmoke")) {
                vmArg("-Dskyglint.smoke.world=true")
                vmArg("-Dskyglint.smoke.miningClockOnly=true")
            }
        } }
    }
}

// Run the actual packaged mod through production-mode Fabric discovery, outside the source checkout.
if (project.hasProperty("smoke")) {
    val installedSmokeJar = tasks.register<Jar>("installedSmokeJar") {
        dependsOn(smoke.classesTaskName)
        archiveFileName.set("reborn-update-smoke.jar")
        destinationDirectory.set(layout.buildDirectory.dir("smoke"))
        from(smoke.output)
    }
    tasks.register<JavaExec>("runInstalledUpdateSmoke") {
        dependsOn(tasks.jar, installedSmokeJar, "downloadAssets")
        mainClass.set("net.fabricmc.loader.impl.launch.knot.KnotClient")
        val sourceOutput = sourceSets.main.get().output.files + smoke.output.files
        classpath = files(provider { tasks.named<JavaExec>("runSmokeClient").get().classpath.filter { it !in sourceOutput } })
        workingDir(layout.projectDirectory.dir("run-update-smoke"))
        systemProperty("fabric.development", "false")
        systemProperty("skyglint.smoke.installed", "true")
        systemProperty("skyglint.smoke.expectedVersion", project.version.toString())
        args("--version", "26.2", "--assetIndex", "26.2-32", "--assetsDir",
            gradle.gradleUserHomeDir.resolve("caches/fabric-loom/assets").absolutePath)
        doFirst {
            val runRoot = layout.projectDirectory.dir("run-update-smoke").asFile
            val modsRoot = runRoot.resolve("mods").apply { mkdirs() }
            tasks.jar.get().archiveFile.get().asFile.copyTo(modsRoot.resolve("reborn-current.jar"), overwrite = true)
            installedSmokeJar.get().archiveFile.get().asFile.copyTo(modsRoot.resolve("reborn-update-smoke.jar"), overwrite = true)
            configurations.runtimeClasspath.get().files.filter {
                it.name == "fabric-api-0.161.0+26.2.jar" || it.name == "fabric-language-kotlin-1.13.13+kotlin.2.4.10.jar"
            }.forEach { it.copyTo(modsRoot.resolve(it.name), overwrite = true) }
            fun fixture(file: String, id: String, version: String, minecraft: String) {
                ZipOutputStream(modsRoot.resolve(file).outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("fabric.mod.json"))
                    zip.write("""{"schemaVersion":1,"id":"$id","version":"$version","environment":"client","depends":{"minecraft":"$minecraft"}}""".toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("update-smoke-payload.txt"))
                    zip.write("Archive intact: $id $version $minecraft".toByteArray())
                    zip.closeEntry()
                }
            }
            val oldFiles = mutableListOf<String>()
            val oldVersions = listOf("0.1") + (1..12).map { "0.1.$it" } + "0.2.0-rc.1"
            oldVersions.forEachIndexed { index, version ->
                val file = "renamed-old-download-$index.jar"
                oldFiles += file
                val previousRelease = layout.buildDirectory.file("libs/reborn-gui-0.1.12.jar").get().asFile
                if (version == "0.1.12" && previousRelease.isFile)
                    previousRelease.copyTo(modsRoot.resolve(file), overwrite = true)
                else fixture(file, "reborn", version, "26.2")
            }
            fixture("unrelated.jar", "reborn_update_test_other", "1.0.0", "26.2")
            fixture("reborn-future-incompatible.jar", "reborn", "99.0.0", "26.1")
            fixture("reborn-old-incompatible.jar", "reborn", "0.1.12", "26.1")
            // Equal compatible duplicates can prevent Fabric resolution; unit tests cover that case.
            fixture("reborn-equal-incompatible.jar", "reborn", project.version.toString(), "26.1")
            runRoot.resolve("config/skyglint/update-smoke-preserved.txt").apply {
                parentFile.mkdirs()
                writeText("Existing config directory content must remain intact")
            }
            runRoot.resolve("config/skyglint/backups/mods/existing-backup.jar.bak").apply {
                parentFile.mkdirs()
                writeText("Existing backup must remain intact")
            }
            fun recordHashes(manifest: String, files: List<String>) {
                val properties = Properties()
                files.forEach { relative ->
                    properties.setProperty(relative, HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(runRoot.resolve(relative).readBytes())))
                }
                runRoot.resolve(manifest).outputStream().use { properties.store(it, "Installed update smoke byte identity") }
            }
            recordHashes("expected-old-jars.properties", oldFiles.map { "mods/$it" })
            recordHashes("expected-preserved-files.properties", listOf(
                "mods/reborn-current.jar", "mods/unrelated.jar", "mods/reborn-future-incompatible.jar",
                "mods/reborn-old-incompatible.jar", "mods/reborn-equal-incompatible.jar",
                "config/skyglint/update-smoke-preserved.txt", "config/skyglint/backups/mods/existing-backup.jar.bak"))
        }
    }
}
