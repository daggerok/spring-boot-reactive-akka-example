plugins {
  idea
  `kotlin-dsl`
}

idea {
  module {
    isDownloadJavadoc = false
    isDownloadSources = false
  }
}

repositories { 
  gradlePluginPortal()
}

kotlinDslPluginOptions {
  experimentalWarning.set(false)
}
