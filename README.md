# scala akka spring-boot gradle-kotlin-dsl [![Build Status](https://travis-ci.org/daggerok/spring-boot-reactive-akka-example.svg?branch=master)](https://travis-ci.org/daggerok/spring-boot-reactive-akka-example)
Build Reactive Scala Akka app with Spring Boot and Gradle Kotlin DSL

## build and run

```bash
./gradlew
java -jar ./build/libs/*.jar
http :8080
http :8080/tweets body=ololo
http :8080/tweets body=trololo author=author
http :8080/tweets
http :8080/tweets/af05253d-d224-4aea-a0b2-2ac0582dea84/tags
```

links:

* see [YouTube: Spring Tips: Bootiful, Reactive Scala](https://www.youtube.com/watch?v=E_YZwrv-zTk)
* update / [migrate Vuepress](https://v1.vuepress.vuejs.org/miscellaneous/migration-guide.html#vuepress-style-styl) 0.x -> 1.x
