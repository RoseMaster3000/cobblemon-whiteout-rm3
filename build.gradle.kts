plugins {
    id("java")
    id("fabric-loom") version("1.9-SNAPSHOT")
    kotlin("jvm") version ("2.1.0")
}

group = property("maven_group")!!
version = property("mod_version")!!

repositories {
    // Keep existing ones using preferred shorthand
    mavenLocal()
    mavenCentral()
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") // GeckoLib
    maven("https://maven.impactdev.net/repository/development/")     // ImpactDev
    maven("https://api.modrinth.com/maven")                          // Modrinth

    // Added repositories using Kotlin DSL
    maven("https://maven.parchmentmc.org")
    maven {
        name = "Ladysnake Mods" // Use property assignment for name
        url = uri("https://maven.ladysnake.org/releases") // Use url = uri(...)
    }
    maven("https://jm.gserv.me/repository/maven-public/")
    maven {
        name = "Fuzs Mod Resources"
        url = uri("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
    }
    // CurseMaven in Kotlin DSL:
    maven {
        url = uri("https://www.cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    maven {
        name = "Jared's maven"
        url = uri("https://maven.blamejared.com/")
    }
    maven {
        name = "ModMaven"
        url = uri("https://modmaven.dev")
    }
    maven("https://jitpack.io")
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Fabric Kotlin
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    // Cobblemon
    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")

    // Lifesteal > gradle publishToMavenLocal
    modImplementation("mc.mian:lifesteal-fabric:${property("lifesteal_version")}")
}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(mutableMapOf("version" to project.version))
        }
    }

    jar {
        from("LICENSE")
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "21"
    }
}