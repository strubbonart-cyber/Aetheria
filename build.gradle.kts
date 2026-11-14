plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.19-SNAPSHOT"
}

group = providers.gradleProperty("maven_group").get()
version = providers.gradleProperty("mod_version").get()

base {
    archivesName.set(providers.gradleProperty("archives_base_name").get())
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    mappings("net.fabricmc:yarn:${providers.gradleProperty("yarn_mappings").get()}:v2")
    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
}

/*
 * Legacy Forge sources remain in src/main/java and src/main/kotlin for reference.
 * Only src/fabric/java is compiled for this Fabric port.
 * Compatible original assets and data are shared.
 */
sourceSets {
    named("main") {
        java.setSrcDirs(listOf("src/fabric/java"))
        resources.setSrcDirs(listOf("src/fabric/resources", "src/main/resources"))
        resources.exclude(
            "mcmod.info",
            "mixins.aetheria.json",
            "accesstransformer.cfg",
            "META-INF/**"
        )
    }
}

loom {
    mods {
        create("skyblockqol") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Build runs on JDK 25 for current Loom; this keeps the resulting mod Java 21-compatible.
    options.release.set(21)
    options.encoding = "UTF-8"
}

java {
    // Do not require a separate JDK 21 toolchain: JDK 25 can compile with --release 21.
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.jar {
    from("LICENSE.txt") {
        rename { "LICENSE_${project.name}" }
    }
}
