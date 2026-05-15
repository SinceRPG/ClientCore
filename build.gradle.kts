plugins {
    id("java-library")
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

repositories {
    mavenCentral()
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
    maven("https://repo.citizensnpcs.co/")
    maven("https://repo.fancyinnovations.com/releases")
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.api.get())
    compileOnly("com.github.retrooper:packetevents-spigot:2.12.1") { isTransitive = false }
    compileOnly("com.github.retrooper:packetevents-api:2.12.1") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.12.2") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.15") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.15") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.9") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.9") { isTransitive = false }
    compileOnly("io.lumine:Mythic-Dist:5.12.0-SNAPSHOT") { isTransitive = false }
    compileOnly("io.lumine:MythicLib-dist:1.7.1-SNAPSHOT") { isTransitive = false }
    compileOnly("net.Indyuce:MMOItems-API:6.10.1-SNAPSHOT") { isTransitive = false }
    compileOnly("net.citizensnpcs:citizens-main:2.0.42-SNAPSHOT") { isTransitive = false }
    compileOnly("de.oliver:FancyNpcs:2.10.0") { isTransitive = false }

    implementation("com.zaxxer:HikariCP:7.0.2")
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    compileOnly("com.mysql:mysql-connector-j:9.6.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}