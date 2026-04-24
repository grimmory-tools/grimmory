import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.hibernate.orm") version "7.3.2.Final"
    id("com.github.ben-manes.versions") version "0.54.0"
    jacoco
}

group = "org.booklore"
version = "0.0.1-SNAPSHOT"

val defaultFrontendDistDir = file("${rootDir}/../frontend/dist/grimmory/browser")
val configuredFrontendDistDir = providers.gradleProperty("frontendDistDir")
    .map { file(it) }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

val useLocalLibs = providers.gradleProperty("useLocalLibs").isPresent

repositories {
    if (useLocalLibs) mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
}

fun pdfiumNativesClassifier(): String {
    // Support cross-compilation: check for explicit target overrides first
    val targetPlatform = System.getenv("TARGETPLATFORM")
        ?: project.findProperty("targetPlatform")?.toString()
    val targetArch = System.getenv("TARGETARCH")
        ?: project.findProperty("targetArch")?.toString()

    val osName: String
    val arch: String

    if (targetPlatform != null) {
        // Docker TARGETPLATFORM format: linux/amd64, linux/arm64
        val parts = targetPlatform.split("/")
        osName = parts.getOrElse(0) { "linux" }
        arch = parts.getOrElse(1) { "amd64" }
    } else {
        osName = System.getProperty("os.name").lowercase()
        arch = targetArch ?: System.getProperty("os.arch").lowercase()
    }

    val osKey = when {
        "win" in osName -> "windows"
        "mac" in osName || "darwin" in osName -> "darwin"
        "nux" in osName || "linux" in osName -> {
            val isMusl = try {
                val libDir = File("/lib")
                libDir.exists() && (libDir.listFiles()?.any { f -> f.name.startsWith("ld-musl-") } == true)
            } catch (_: Exception) {
                try {
                    File("/proc/self/maps").readText().contains("musl")
                } catch (_: Exception) { false }
            }
            if (isMusl) "linux-musl" else "linux"
        }
        else -> error("Unsupported OS: $osName")
    }

    val archKey = when (arch) {
        "x86_64", "amd64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> error("Unsupported architecture: $arch")
    }

    return "natives-$osKey-$archKey"
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    // --- Spring Boot ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // --- Reactive Streams ---
    implementation("io.projectreactor:reactor-core")

    // --- Database & Migration ---
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.8")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql:12.4.0")

    // --- Security & Authentication ---
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // --- Lombok (For Clean Code) ---
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")

    // --- Book & Image Processing ---
    val pdfium4jVersion = if (useLocalLibs) "+" else "0.14.0"
    implementation("org.grimmory:pdfium4j:$pdfium4jVersion")
    runtimeOnly("org.grimmory:pdfium4j:$pdfium4jVersion:${pdfiumNativesClassifier()}")

    // --- TwelveMonkeys ImageIO ---
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.13.1")
    implementation("com.twelvemonkeys.imageio:imageio-tiff:3.13.1")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.13.1")
    implementation("com.twelvemonkeys.imageio:imageio-bmp:3.13.1")

    // epub4j-grimmory fork publishes as org.grimmory:epub4j-core
    val epub4jCoords = if (useLocalLibs) "org.grimmory:epub4j-core:+" else "org.grimmory:epub4j-core:1.2.0"
    implementation(epub4jCoords)

    // epub4j-native for native archive parsing
    val epub4jNativeCoords = if (useLocalLibs) "org.grimmory:epub4j-native:+" else "org.grimmory:epub4j-native:1.2.0"
    implementation(epub4jNativeCoords)

    // --- Audio Metadata (Audiobook Support) ---
    implementation("com.github.RouHim:jaudiotagger:2.0.19")

    // --- Archive Support ---
    implementation("com.github.gotson.nightcompress:nightcompress:1.1.1")

    // --- JSON & Web Scraping ---
    implementation("org.jsoup:jsoup:1.22.2")

    // --- Mapping (DTOs & Entities) ---
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    // --- API Documentation ---
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.0.3")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12") // Required by commons-compress for 7z support
    implementation("org.apache.commons:commons-text:1.15.0")

    // --- MIME Detection ---
    implementation("org.apache.tika:tika-core:3.3.0")

    // --- XML Support (JAXB) ---
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.5")
    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:4.0.7")

    // --- Template Engine ---
    implementation("org.freemarker:freemarker:2.3.34")

    // --- Jackson 3 ---
    implementation(platform("tools.jackson:jackson-bom:3.1.2"))
    implementation("tools.jackson.core:jackson-core")
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.module:jackson-module-blackbird")

    // --- Jackson 2 (Compatibility) ---
    // jackson-annotations version is managed by Jackson 3 BOM (requires 2.20+)
    implementation("com.fasterxml.jackson.core:jackson-annotations")

    // --- Caching ---
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

    // --- Test Dependencies ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testRuntimeOnly("com.h2database:h2")

    // PDFBox for test PDF creation only (production code uses PDFium4j)
    testImplementation("org.apache.pdfbox:pdfbox:3.0.7")
}

hibernate {
    enhancement {
        enableAssociationManagement = false
        enableLazyInitialization = true
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxHeapSize = "2560m"
    jvmArgs("-XX:+EnableDynamicAgentLoading", "--enable-native-access=ALL-UNNAMED", "--enable-preview")
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<Copy>("processResources") {
    val frontendResourcesDir = configuredFrontendDistDir
        .orElse(providers.provider { defaultFrontendDistDir })
        .get()

    inputs.property("frontendDistDir", frontendResourcesDir.absolutePath)
    inputs.property("hasFrontendResources", frontendResourcesDir.exists())

    if (frontendResourcesDir.exists()) {
        from(frontendResourcesDir) {
            into("static")
        }
    }
}

tasks.named<BootRun>("bootRun") {
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--enable-preview")
    if (System.getenv("REMOTE_DEBUG_ENABLED") == "true") {
        jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
    }
}

tasks.named<BootJar>("bootJar") {
    mainClass.set("org.booklore.BookloreApplication")
}
