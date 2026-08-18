plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.synix"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // VaultAPI isn't published to Maven Central; JitPack is the standard
    // source for it.
    maven("https://jitpack.io")
}

dependencies {
    // provided at runtime by the server — never shaded into the jar.
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Vault itself (and the economy provider it exposes) is a separate
    // plugin installed on the server, if present — compileOnly here too.
    // VaultAPI declares an ancient org.bukkit:bukkit dependency, which
    // collides with paper-api over the same Bukkit capability and fails
    // resolution outright. Only Vault's own interfaces are needed here;
    // the Bukkit classes come from paper-api.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
}

tasks {
    // Paper API targets Java 21; match it so records/switch-expressions
    // used in this plugin behave identically to how the server runs it.
    compileJava {
        options.release.set(21)
    }

    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("synix-websync")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
