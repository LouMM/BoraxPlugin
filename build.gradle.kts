import java.net.URLClassLoader
import java.lang.reflect.Modifier

plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // This gives you Paper API + full Mojang-mapped NMS (NBT, etc.)
    //paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    // Creates the final shaded + reobfuscated JAR
    reobfJar {
        outputJar.set(layout.buildDirectory.file("libs/PlayerLocs-${project.version}.jar"))
    }

    processResources {
        val props = mapOf(
            "version" to project.version,
            "name" to project.name
        )
        inputs.properties(props)
        filteringCharset = "UTF-8"

        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    register("printServerMethods") {
        doLast {
            val cp = sourceSets["main"].compileClasspath
            val cl = URLClassLoader(cp.map { it.toURI().toURL() }.toTypedArray())
            val clazz = cl.loadClass("org.bukkit.Server")
            clazz.declaredMethods.forEach { m: java.lang.reflect.Method ->
                if (m.name.contains("OfflinePlayer")) {
                    println("${m.name}(${m.parameterTypes.joinToString { it.simpleName }}) -> ${m.returnType.simpleName}")
                }
            }
        }
    }
}