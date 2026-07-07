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

val compatModern by sourceSets.creating {
    java.srcDir("src/compatModern/java")
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:${property("paper_api_version")}")
    testImplementation("net.dmulloy2:ProtocolLib:5.4.0")
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

tasks.test {
    useJUnitPlatform()
    classpath += compatModern.output
}

tasks.jar {
    dependsOn(tasks.named(compatModern.classesTaskName))
    archiveBaseName.set("${project.property("archives_base_name")}-paper-1.21")
    from(compatModern.output)
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Oreveil-Compatibility-Adapter" to "modern-1.21",
        )
    }
}

tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(compatModern.allSource)
}

tasks.register("buildAllTargets") {
    group = "build"
    description = "Builds all Oreveil version-targeted plugin jars."
    dependsOn(tasks.build)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
