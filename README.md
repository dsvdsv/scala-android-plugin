# Scala language support for Android #

[![Actions Status](https://github.com/dsvdsv/scala-android-plugin/workflows/Continuous%20Integration/badge.svg)](https://github.com/dsvdsv/scala-android-plugin/actions)
[![Download](https://api.bintray.com/packages/dsvdsv/maven/scala-android-plugin/images/download.svg)](https://bintray.com/dsvdsv/maven/scala-android-plugin/_latestVersion)

scala-android-plugin adds scala language support to official gradle android plugin.
See also [sample projects](https://github.com/dsvdsv/android-fp-sample)

## Supported versions

| Scala          | Gradle | Android Plugin |
|----------------|--------|----------------|
| 2.11.x, 2.13.x | 8.2.x  | 8.2.x          |
| 2.11.x, 2.13.x | 7.0.2  | 7.0.x          |

To work with recent versions (8.x) of Gradle and Android Plugin, you need to build this project from the source. See [Build from the source and apply it to your project](#build-from-the-source-and-apply-it-to-your-project) section.

## Installation

### Add buildscript's dependency

`build.gradle`
```groovy
buildscript {
    dependencies {
        classpath 'com.android.tools.build:gradle:4.0.2'
        classpath 'scala.android.plugin:scala-android-plugin:20210222.1057'
    }
}
```

### Apply plugin

`build.gradle`
```groovy
apply plugin: 'com.android.application'
apply plugin: 'io.github.dsvdsv.scala-android'
```

### Add scala-library dependency

The plugin decides scala language version using scala-library's version.

`build.gradle`
```groovy
dependencies {
    implementation "org.scala-lang:scala-library:2.13.5"
}
```

## Build from the source and apply it to your project

 * Checkout this repository
 * Run `publishToMavenLocal` gradle command
 * In the console log, the artifact name `io.github.dsvdsv:scala-android-plugin:YYYYMMDD.hhmm` will be displayed (version changed for each time).
 * Set it in your project's `build.gradle`. 
```groovy
buildscript {
    repositories {
        mavenLocal() // needed to access local repository
        // ...
    }
    dependencies {
        classpath 'scala.android.plugin:scala-android-plugin:YYYYMMDD.hhmm'
        // ...
    }
}
```
