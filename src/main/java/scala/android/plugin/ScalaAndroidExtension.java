package scala.android.plugin;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;
import scala.android.plugin.api.AndroidScalaSourceSet;
import scala.android.plugin.internal.AndroidScalaSourceSetFactory;

public class ScalaAndroidExtension {

    private final NamedDomainObjectContainer<AndroidScalaSourceSet> sourceSetsContainer;
    private Closure<Void> configClosure;

    public ScalaAndroidExtension(Project project, Instantiator instantiator) {
        this.sourceSetsContainer = project.container(
                AndroidScalaSourceSet.class,
                new AndroidScalaSourceSetFactory(instantiator, project.getObjects())
        );

        sourceSetsContainer.whenObjectAdded(scalaSourceSet -> {
            scalaSourceSet.getScala();
        });
    }


    public NamedDomainObjectContainer<AndroidScalaSourceSet> getSourceSetsContainer() {
        return sourceSetsContainer;
    }

    /**
     * Configure {@link ScalaCompile} options
     * <p>
     * Here is an example of setting compiler customizers for compilation, turning on the
     * annotation processing, and setting the source and target compilation to java 1.7:
     * <pre>
     * androidScala {
     *   options {
     *     configure(scalaOptions) {
     *          ....
     *     }
     *
     *   }
     * }
     * </pre>
     * <p>
     *
     * @param config configuration closure that will be applied to all {@link ScalaCompile} tasks for the
     *               android project.
     */

    void options(Closure<Void> config) {
        configClosure = config;
    }

    /**
     * Configure the sources for Groovy in the same way Java sources can be configured for Android.
     * <p>
     * For example:
     * <pre>
     * androidScala {
     *   sourceSets {
     *     main {
     *       scala {
     *         srcDirs = ['src/main/scala', 'src/main/java', 'src/shared/scala']
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * @param configClosure the configuration block that configures
     */
    void sourceSets(Action<NamedDomainObjectContainer<AndroidScalaSourceSet>> configClosure) {
        configClosure.execute(sourceSetsContainer);
    }

    void configure(ScalaCompile task) {
        if (configClosure != null) {
            ConfigureUtil.configure(configClosure, task);
        }
    }
}
