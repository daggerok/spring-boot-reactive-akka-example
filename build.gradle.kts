import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
  idea
  scala
  id("com.moowork.node") version Globals.nodePluginVersion
  id("org.springframework.boot") version Globals.springBootVersion
  id("com.github.ben-manes.versions") version Globals.versionsPluginVersion
  id("com.avast.gradle.docker-compose") version Globals.dockerComposePluginVersion
  id("io.spring.dependency-management") version Globals.dependencyManagementPluginVersion
}

extra["scala.version"] = Globals.scalaVersion
extra["junit-jupiter.version"] = Globals.junitJupiterVersion

allprojects {
  group = Globals.groupId
  version = Globals.version
}

java {
  sourceCompatibility = Globals.javaVersion
  targetCompatibility = Globals.javaVersion
}

sourceSets {
  main {
    java.srcDir("src/main/scala")
  }
  test {
    java.srcDir("src/test/scala")
  }
}

repositories {
  mavenCentral()
  maven(url = "https://repo.spring.io/snapshot")
  maven(url = "https://repo.spring.io/milestone")
}

dependencies {
  implementation(platform("org.springframework.boot:spring-boot-dependencies:${Globals.springBootVersion}"))
  implementation(platform("org.reactivestreams:reactive-streams:${Globals.reactiveStreamsVersion}"))

  implementation("com.typesafe.akka:akka-stream_${Globals.scalaMajorVersion}:${Globals.akkaStreamVersion}")
  implementation("org.scala-lang:scala-library:${Globals.scalaVersion}")
  implementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")

  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  runtimeOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")

  testImplementation("junit:junit")
  testImplementation(platform("org.junit:junit-bom:${Globals.junitJupiterVersion}"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
  testRuntime("org.junit.platform:junit-platform-launcher")
}

defaultTasks("build")

tasks {
  getByName("clean") {
    doLast {
      delete(
          project.buildDir,
          "${project.projectDir}/.vuepress/dist"
      )
    }
  }

  withType<Wrapper>().configureEach {
    gradleVersion = Globals.gradleWrapperVersion
    distributionType = Wrapper.DistributionType.BIN
  }

  withType<ScalaCompile> {
    scalaCompileOptions.additionalParameters = listOf("-deprecation")
  }

  withType<BootJar>().configureEach {
    launchScript()
  }

  withType<Test> {
    useJUnitPlatform()
    testLogging {
      showExceptions = true
      showStandardStreams = true
      events(PASSED, SKIPPED, FAILED)
    }
  }

  create<Zip>("sources") {
    group = "Archive"
    description = "Archives sources in a zip archive"
    dependsOn("clean")
    shouldRunAfter("clean")
    from(".vuepress") {
      into(".vuepress")
    }
    from("src") {
      into("src")
    }
    from(
        ".gitignore",
        "build.gradle.kts",
        "docker-compose.yaml",
        "gradle.properties",
        "LICENSE",
        "package.json",
        "package-lock.json",
        "README.md",
        "settings.gradle.kts"
    )
    archiveFileName.set("${project.buildDir}/sources-${project.version}.zip")
  }

  // gradle dependencyUpdates -Drevision=release --parallel
  named<DependencyUpdatesTask>("dependencyUpdates") {
    resolutionStrategy {
      componentSelection {
        all {
          val rejected = listOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea", "SNAPSHOT")
              .map { qualifier -> Regex("(?i).*[.-]$qualifier[.\\d-+]*") }
              .any { it.matches(candidate.version) }
          if (rejected) reject("Release candidate")
        }
      }
    }
  }
}

node {
  download = true
  version = Globals.nodeVersion
  npmVersion = Globals.npmVersion
}

tasks.create("start")
tasks["start"].dependsOn("npm_start")
tasks["npm_start"].dependsOn("npm_i")
tasks["build"].dependsOn("npm_run_build")
tasks["npm_run_build"].dependsOn("npm_i")
tasks["npm_run_hg-pages"].dependsOn("npm_i")

tasks["composeUp"].dependsOn("assemble")
tasks["composeUp"].shouldRunAfter("clean", "assemble")
//val composePs: Task = tasks.create<Exec>("composePs") {
//  shouldRunAfter("clean", "assemble")
//  commandLine("docker-compose", "ps")
//}
//dockerCompose {
//  isRequiredBy(composePs)
//}
