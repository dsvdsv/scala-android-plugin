package scala.android.plugin.internal

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class FunctionalSpec extends GradleSpec implements FileHelper {

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile

    GradleRunner runner(String... args) {
        runner(testProjectDir.root, args)
    }

    BuildResult runDebug(String... args) {
        runner(args).withDebug(true)
                .build()
    }

    BuildResult run(String... args) {
        runner(args).build()
    }

}