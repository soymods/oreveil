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

val paper118CompileOnly by configurations.creating

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    paper118CompileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    paper118CompileOnly("net.dmulloy2:ProtocolLib:5.4.0")

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

val compilePaper118Java by tasks.registering(JavaCompile::class) {
    description = "Compiles the Paper 1.18 target against the Paper 1.18 API."
    source(sourceSets.main.get().java, compatCaves.java)
    classpath = paper118CompileOnly
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/paper118"))
    options.encoding = "UTF-8"
    options.release.set(17)
}

val processPaper118Resources by tasks.registering(Copy::class) {
    val props = mapOf(
        "version" to project.version,
        "minecraftVersion" to "1.18.2",
        "apiVersion" to "1.18",
        "compatAdapterClass" to "com.soymods.oreveil.compat.caves.CavesServerCompatibility",
    )

    inputs.properties(props)
    filteringCharset = "UTF-8"
    from(sourceSets.main.get().resources)
    into(layout.buildDirectory.dir("resources/paper118"))
    filesMatching(listOf("plugin.yml", "oreveil-compat.properties")) {
        expand(props)
    }
}

val paper118Jar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the Paper 1.18 targeted plugin jar."
    dependsOn(compilePaper118Java, processPaper118Resources)
    archiveBaseName.set("${project.property("archives_base_name")}-paper-1.18")
    archiveVersion.set(project.version.toString())
    from(compilePaper118Java.flatMap { it.destinationDirectory })
    from(processPaper118Resources)
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Oreveil-Compatibility-Adapter" to "caves-1.18",
        )
    }
}

tasks.register("buildAllTargets") {
    group = "build"
    description = "Builds all Oreveil version-targeted plugin jars."
    dependsOn(tasks.build, paper118Jar)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
