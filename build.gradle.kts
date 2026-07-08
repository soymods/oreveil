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

val compatCaves by sourceSets.creating {
    java.srcDir("src/compatCaves/java")
}

val paper116CompileOnly by configurations.creating
val paper117CompileOnly by configurations.creating
val paper118CompileOnly by configurations.creating
val paper1206CompileOnly by configurations.creating
val paper26CompileOnly by configurations.creating

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    paper116CompileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    paper116CompileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    paper117CompileOnly("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")
    paper117CompileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    paper118CompileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    paper118CompileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    paper1206CompileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    paper1206CompileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    paper26CompileOnly("io.papermc.paper:paper-api:26.2.build.53-alpha")
    paper26CompileOnly("net.dmulloy2:ProtocolLib:5.4.0")

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
        "apiVersion" to "1.21",
        "compatAdapterClass" to "com.soymods.oreveil.compat.modern.ModernServerCompatibility",
    )

    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "oreveil-compat.properties")) {
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
    from(compatCaves.allSource)
}

val compilePaper118To1204Java by tasks.registering(JavaCompile::class) {
    description = "Compiles the Paper 1.18-1.20.4 target against the Paper 1.18 API baseline."
    source(sourceSets.main.get().java, compatCaves.java)
    classpath = paper118CompileOnly
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/paper118To1204"))
    options.encoding = "UTF-8"
    options.release.set(17)
}

val compilePaper117Java by tasks.registering(JavaCompile::class) {
    description = "Compiles the Paper 1.17.x target against the Paper 1.17.1 API."
    source(sourceSets.main.get().java, compatCaves.java)
    classpath = paper117CompileOnly
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/paper117"))
    options.encoding = "UTF-8"
    options.release.set(16)
}

val compilePaper1205To1206Java by tasks.registering(JavaCompile::class) {
    description = "Compiles the Paper 1.20.5-1.20.6 target against the Paper 1.20.6 API."
    source(sourceSets.main.get().java, compatCaves.java)
    classpath = paper1206CompileOnly
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/paper1205To1206"))
    options.encoding = "UTF-8"
    options.release.set(21)
}

val compilePaper26Java by tasks.registering(JavaCompile::class) {
    description = "Compiles the Paper 26.x target against the Paper 26.2 API."
    source(sourceSets.main.get().java, compatModern.java)
    classpath = paper26CompileOnly
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/paper26"))
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    options.encoding = "UTF-8"
    options.release.set(25)
}

val compilePaper116Java by tasks.registering(JavaCompile::class) {
    description = "Compiles the Paper 1.16.x target against the Paper 1.16.5 API."
    source(sourceSets.main.get().java, compatCaves.java)
    classpath = paper116CompileOnly
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/paper116"))
    options.encoding = "UTF-8"
    options.release.set(16)
}

fun registerCavesJar(targetName: String, supportedVersions: String, apiVersion: String = "1.18"): TaskProvider<Jar> {
    val resourceTask = tasks.register<Copy>("process${targetName.toTaskSuffix()}Resources") {
        val props = mapOf(
            "version" to project.version,
            "minecraftVersion" to supportedVersions,
            "apiVersion" to apiVersion,
            "compatAdapterClass" to "com.soymods.oreveil.compat.caves.CavesServerCompatibility",
        )

        inputs.properties(props)
        filteringCharset = "UTF-8"
        from(sourceSets.main.get().resources)
        into(layout.buildDirectory.dir("resources/${targetName.toTaskSuffix()}"))
        filesMatching(listOf("plugin.yml", "oreveil-compat.properties")) {
            expand(props)
        }
    }

    return tasks.register<Jar>("${targetName.toTaskSuffix()}Jar") {
        group = "build"
        description = "Builds the $targetName targeted plugin jar."
        val compileTask = when (targetName) {
            "paper-1.16.x" -> compilePaper116Java
            "paper-1.17.x" -> compilePaper117Java
            "paper-1.20.5-1.20.6" -> compilePaper1205To1206Java
            else -> compilePaper118To1204Java
        }
        dependsOn(compileTask, resourceTask)
        archiveBaseName.set("${project.property("archives_base_name")}-$targetName")
        archiveVersion.set(project.version.toString())
        from(compileTask.flatMap { it.destinationDirectory })
        from(resourceTask)
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Oreveil-Compatibility-Adapter" to "caves-1.18",
                "Oreveil-Supported-Minecraft" to supportedVersions,
            )
        }
    }
}

fun String.toTaskSuffix(): String = split('-', '.')
    .filter { it.isNotBlank() }
    .joinToString("") { part -> part.replaceFirstChar { char -> char.uppercase() } }

fun registerModernJar(targetName: String, supportedVersions: String, apiVersion: String): TaskProvider<Jar> {
    val resourceTask = tasks.register<Copy>("process${targetName.toTaskSuffix()}Resources") {
        val props = mapOf(
            "version" to project.version,
            "minecraftVersion" to supportedVersions,
            "apiVersion" to apiVersion,
            "compatAdapterClass" to "com.soymods.oreveil.compat.modern.ModernServerCompatibility",
        )

        inputs.properties(props)
        filteringCharset = "UTF-8"
        from(sourceSets.main.get().resources)
        into(layout.buildDirectory.dir("resources/${targetName.toTaskSuffix()}"))
        filesMatching(listOf("plugin.yml", "oreveil-compat.properties")) {
            expand(props)
        }
    }

    return tasks.register<Jar>("${targetName.toTaskSuffix()}Jar") {
        group = "build"
        description = "Builds the $targetName targeted plugin jar."
        dependsOn(compilePaper26Java, resourceTask)
        archiveBaseName.set("${project.property("archives_base_name")}-$targetName")
        archiveVersion.set(project.version.toString())
        from(compilePaper26Java.flatMap { it.destinationDirectory })
        from(resourceTask)
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Oreveil-Compatibility-Adapter" to "modern-26",
                "Oreveil-Supported-Minecraft" to supportedVersions,
            )
        }
    }
}

val paper116xJar = registerCavesJar("paper-1.16.x", "1.16.x", "1.16")
val paper117xJar = registerCavesJar("paper-1.17.x", "1.17.x", "1.17")
val paper118xJar = registerCavesJar("paper-1.18.x", "1.18.x")
val paper119xJar = registerCavesJar("paper-1.19.x", "1.19.x")
val paper1200To1204Jar = registerCavesJar("paper-1.20.0-1.20.4", "1.20.0-1.20.4")
val paper1205To1206Jar = registerCavesJar("paper-1.20.5-1.20.6", "1.20.5-1.20.6", "1.20")
val paper26xJar = registerModernJar("paper-26.x", "26.x", "26.2")

tasks.register("buildAllTargets") {
    group = "build"
    description = "Builds all Oreveil version-targeted plugin jars."
    dependsOn(tasks.build, paper116xJar, paper117xJar, paper118xJar, paper119xJar, paper1200To1204Jar, paper1205To1206Jar, paper26xJar)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
