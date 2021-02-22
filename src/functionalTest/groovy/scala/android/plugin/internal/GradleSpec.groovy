package scala.android.plugin.internal

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification

class GradleSpec extends Specification {

    GradleRunner runner(File dir, String... args) {
        def argsList = args.toList()
        return GradleRunner.create()
                .withProjectDir(dir)
                .forwardOutput()
                .withPluginClasspath()
                .withArguments(argsList)
    }

    BuildResult runDebug(File dir, String... args) {
        runner(dir, args).withDebug(true)
                .build()
    }
}
