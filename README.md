# Scala language support for Android #

[![Actions Status](https://github.com/dsvdsv/scala-android-plugin/workflows/Continuous%20Integration/badge.svg)](https://github.com/dsvdsv/scala-android-plugin/actions)
[![Download](https://api.bintray.com/packages/dsvdsv/maven/scala-android-plugin/images/download.svg)](https://bintray.com/dsvdsv/maven/scala-android-plugin/_latestVersion)

scala-android-plugin adds scala language support to official gradle android plugin.
See also [sample projects](https://github.com/dsvdsv/android-fp-sample)

## Supported versions

| Scala  | Gradle | Android Plugin |
| ------ | ------ |----------------|
| 2.13.* | 6.6    | 4.0.0, 4.0.2   |
| 2.13.* | 7.0.2    | 7.0.0          |

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
apply plugin: 'scala.android'
```

### Add scala-library dependency

The plugin decides scala language version using scala-library's version.

`build.gradle`
```groovy
dependencies {
    implementation "org.scala-lang:scala-library:2.13.5"
}
```