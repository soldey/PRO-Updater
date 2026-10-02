import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    java
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    kotlin("jvm") version "2.4.10"
}

val fabricLoaderVersion: String by project
val fabricKotlinVersion: String by project
val javaVersion: String by project
val modVersion: String by project
val mavenGroup: String by project
val archivesBaseName: String by project

/**
 * Everything that differs between the Minecraft versions we build for. The sources are shared;
 * only these coordinates change, so adding a version is one line here.
 */
data class McTarget(
    /** The exact Minecraft build to compile against. */
    val minecraft: String,
    /** The range written into fabric.mod.json, so patch releases of the same line are accepted. */
    val dependency: String,
    val fabricApi: String,
    val modMenu: String,
)

val mcTargets = mapOf(
    "26.1" to McTarget(
        minecraft = "26.1.2",
        dependency = "~26.1",
        fabricApi = "0.155.2+26.1.2",
        modMenu = "18.0.1",
    ),
    "26.2" to McTarget(
        minecraft = "26.2",
        dependency = "~26.2",
        fabricApi = "0.161.0+26.2",
        modMenu = "20.0.2",
    ),
)

val mcTarget: String = (findProperty("mcTarget") as String?) ?: "26.1"
val target = mcTargets[mcTarget]
    ?: error("Unknown mcTarget '$mcTarget', pick one of ${mcTargets.keys.joinToString()}")

group = mavenGroup
// The Minecraft version is build metadata, which keeps the jars of one release apart.
version = "$modVersion+mc$mcTarget"
base.archivesName.set(archivesBaseName)

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
    withSourcesJar()
}

// Only the compat layer differs between versions; everything else is shared.
sourceSets.main {
    kotlin.srcDir("src/main/kotlin-$mcTarget")
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net") {
        content { includeGroupAndSubgroups("net.fabricmc") }
    }
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${target.minecraft}")

    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:${target.fabricApi}")
    implementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    // Optional at runtime: the modmenu entrypoint is only loaded when ModMenu is installed.
    implementation("maven.modrinth:modmenu:${target.modMenu}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

loom {
    runs {
        named("client") {
            isIdeConfigGenerated = true
            vmArgs("-Xmx2G")
        }
        removeIf { it.name == "server" }
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "minecraft" to target.dependency,
        "fabricLoader" to fabricLoaderVersion,
        "fabricKotlin" to fabricKotlinVersion,
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(javaVersion.toInt())
}

tasks.jar {
    // LGPL 3.0 is the GPL plus extra permissions, so both texts travel with the jar.
    from("LICENSE") { rename { "LICENSE.txt" } }
    from("COPYING") { rename { "COPYING.txt" } }
}
