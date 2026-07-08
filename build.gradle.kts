plugins {
    java
    `maven-publish`
}

import org.gradle.api.attributes.java.TargetJvmVersion

group = property("maven_group") as String
version = property("plugin_version") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

sourceSets {
    main {
        java.setSrcDirs(listOf(
            "src/main/java",
            "src/compatCaves/java",
            "src/compatModern/java",
        ))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // The universal artifact is linked against the oldest supported Paper API.
    // Newer behavior must remain behind compatibility adapters or reflection.
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:${property("paper_api_version")}")
    testImplementation("net.dmulloy2:ProtocolLib:5.4.0")
}

configurations.named("compileClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
}

configurations.matching { it.name == "testCompileClasspath" || it.name == "testRuntimeClasspath" }.configureEach {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraftVersion" to "1.16.x-1.21.x | 26.x",
        "apiVersion" to "1.16",
    )

    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(16)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set(project.property("archives_base_name") as String)
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Oreveil-Supported-Minecraft" to "1.16.x-1.21.x, 26.x",
        )
    }
}

// Retain the old command as a compatibility alias for release automation.
tasks.register("buildAllTargets") {
    group = "build"
    description = "Builds the universal Oreveil plugin jar."
    dependsOn(tasks.build)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
