import org.gradle.api.JavaVersion

object Globals {
  const val artifactId = "spring-boot-reactive-akka-example"
  const val groupId = "com.github.daggerok"
  const val version = "1.0.2-SNAPSHOT"

  val javaVersion = JavaVersion.VERSION_1_8

  const val scalaVersion = "2.13.0"
  const val scalaMajorVersion = "2.13"
  const val akkaStreamVersion = "2.5.23"
  const val gradleWrapperVersion = "5.6-rc-1"
  const val junitJupiterVersion = "5.5.1"
  const val reactiveStreamsVersion = "1.0.2"
  const val springBootVersion = "2.2.0.M4"

  const val npmVersion = "6.9.0"
  const val nodeVersion = "11.13.0"
  const val nodePluginVersion = "1.2.0" // "1.3.1"

  const val versionsPluginVersion = "0.21.0"
  const val dockerComposePluginVersion = "0.9.4"
  const val dependencyManagementPluginVersion = "1.0.8.RELEASE"
}
