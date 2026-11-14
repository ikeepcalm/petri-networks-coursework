plugins {
    id("java")
    id("application")
}

group = "dev.ua.ikeepcalm"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.knowm.xchart:xchart:4.0.4")

    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    testCompileOnly("org.projectlombok:lombok:1.18.48")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.48")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "dev.ua.ikeepcalm.app.Main"
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("experiments") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.ua.ikeepcalm.app.Experiments"
}

tasks.register<JavaExec>("gui") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.ua.ikeepcalm.app.Gui"
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
