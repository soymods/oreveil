plugins {
    java
    `maven-publish`
}

group = property("maven_group") as String
version = property("plugin_version") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraftVersion" to project.property("minecraft_version"),
    )

    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
