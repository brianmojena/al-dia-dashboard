plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}
// Plugin de Google Services (avisos push de Android). Se carga por classpath y
// no con alias(plugins) porque su "plugin marker" no está en los repositorios
// de plugins; el jar sí está en el Maven de Google.
buildscript {
    repositories {
        google()
        mavenCentral()
        // dl.google.com no responde desde algunas redes (p. ej. Cuba); el
        // espejo público de Maven de Google sirve el mismo jar.
        maven("https://maven.aliyun.com/repository/google")
    }
    dependencies {
        classpath(libs.google.services.classpath)
    }
}
