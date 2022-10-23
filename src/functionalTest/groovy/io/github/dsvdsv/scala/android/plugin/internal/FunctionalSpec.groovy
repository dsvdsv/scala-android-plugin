package io.github.dsvdsv.scala.android.plugin.internal

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.TempDir

abstract class FunctionalSpec extends GradleSpec implements FileHelper {

    @TempDir
    File testProjectDir
    File buildFile

    GradleRunner runner(String... args) {
        runner(testProjectDir, args)
    }

    BuildResult runDebug(String... args) {
        runner(args).withDebug(true)
                .build()
    }

    BuildResult run(String... args) {
        runner(args).build()
    }

}