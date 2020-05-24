import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetPreset

plugins {
    kotlin("multiplatform")
}

val androidPresets = mapOf(
  "arm32" to "androidNativeArm32",
  "arm64" to "androidNativeArm64",
  "x86" to "androidNativeX86",
  "x64" to "androidNativeX64"
)

val directories = mapOf(
  "arm32" to "armeabi-v7a",
  "arm64" to "arm64-v8a",
  "x86" to "x86",
  "x64" to "x86_64"
)

kotlin {
  androidPresets.forEach {
    val preset = kotlin.presets[it.value] as KotlinNativeTargetPreset
    targetFromPreset(preset, it.key) {
      binaries {
        sharedLib {
//          freeCompilerArgs += "-opt"
          outputDirectory = File("${project(":binarizer").projectDir}/src/main/jniLibs/${directories[it.key]}/")
          baseName = "ktnative"
        }
      }
    }
  }

  sourceSets {
    val arm64Main by getting {}
    val arm32Main by getting {}
    val x64Main by getting {}
    val x86Main by getting {}
    arm32Main.dependsOn(arm64Main)
    x64Main.dependsOn(arm64Main)
    x86Main.dependsOn(arm64Main)
  }
}
