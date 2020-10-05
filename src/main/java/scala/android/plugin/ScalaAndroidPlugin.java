package scala.android.plugin;

import com.android.build.gradle.*;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.plugins.BasePlugin;
import com.android.builder.model.SourceProvider;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.android.plugin.api.AndroidScalaSourceSet;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class ScalaAndroidPlugin implements Plugin<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScalaAndroidPlugin.class);

    private static final List<String> ANDROID_PLUGIN_NAMES = Arrays.asList(
            "android", "com.android.application", "android-library", "com.android.library",
            "com.android.test", "com.android.feature"
    );

    public void apply(Project project) {
        BasePlugin androidPlugin = (BasePlugin) ANDROID_PLUGIN_NAMES.stream()
                .map(n -> project.getPlugins().findPlugin(n))
                .findFirst()
                .orElseThrow(() ->
                        new GradleException("You must apply the Android plugin or the Android library plugin before using the scala-android plugin")
                );

        LOGGER.debug("Found Plugin: {}", androidPlugin);

        ScalaAndroidExtension scalaAndroidExt = project.getExtensions().getByType(ScalaAndroidExtension.class);

        BaseExtension androidExt = (BaseExtension) project.getExtensions().getByName("android");

        androidExt.getSourceSets().all(sourceSet -> {
            if (!(sourceSet instanceof HasConvention)) {
                return;
            }

            String sourceSetName = sourceSet.getName();
            File sourceSetPath = project.file("src/" + sourceSetName + "/scala");

            if (!sourceSetPath.exists()) {
                LOGGER.debug("SourceSet path does not exists for {} {}", sourceSetName, sourceSetPath);
                return;
            }

            sourceSet.getJava().srcDir(sourceSetPath);

            AndroidScalaSourceSet scalaSourceSet = scalaAndroidExt.getSourceSetsContainer().maybeCreate(sourceSetName);
            ((HasConvention) sourceSet).getConvention().getPlugins().put("scala", scalaSourceSet);
            SourceDirectorySet scalaDirSet = scalaSourceSet.getScala();
            scalaDirSet.srcDir(scalaSourceSet);
            LOGGER.debug("Created scala sourceDirectorySet at {}", scalaDirSet.getSrcDirs());
        });

        project.afterEvaluate(p -> {
            forEachVariant(project, androidExt, variant -> processVariant(variant, project, androidExt, androidPlugin, scalaAndroidExt));
        });
    }

    private static void forEachVariant(Project project, BaseExtension androidExtension, Consumer<BaseVariant> action) {
        LOGGER.debug("Project has {} as an extension", androidExtension);

        if (androidExtension instanceof AppExtension) {
            ((AppExtension) androidExtension).getApplicationVariants().forEach(action);
        }

        if (androidExtension instanceof LibraryExtension) {
            ((LibraryExtension) androidExtension).getLibraryVariants().forEach(action);
        }

        if (androidExtension instanceof TestExtension) {
            ((TestExtension) androidExtension).getApplicationVariants().forEach(action);
        }

        if (androidExtension instanceof TestedExtension) {
            ((TestedExtension) androidExtension).getTestVariants().forEach(action);
            ((TestedExtension) androidExtension).getUnitTestVariants().forEach(action);
        }
    }

    private static void processVariant(
            BaseVariant variantData,
            Project project,
            BaseExtension androidExtension,
            BasePlugin androidPlugin,
            ScalaAndroidExtension scalaAndroidExt
    ) {
        String variantName = variantData.getName();
        LOGGER.debug("Processing variant {}", variantName);

        JavaCompile javaTask = variantData.getJavaCompileProvider().get();
        if (javaTask == null) {
            LOGGER.info("javaTask it null for {}", variantName);
            return;
        }

        String taskName = javaTask.getName().replace("Java", "Scala");
        ScalaCompile scalaTask = project.getTasks().create(taskName, ScalaCompile.class);

        scalaTask.setTargetCompatibility(javaTask.getTargetCompatibility());
        scalaTask.setSourceCompatibility(javaTask.getSourceCompatibility());
        scalaAndroidExt.configure(scalaTask);


        scalaTask.setDestinationDir(javaTask.getDestinationDir());
        scalaTask.setClasspath(javaTask.getClasspath());
        scalaTask.dependsOn(javaTask.getDependsOn());
        scalaTask.setScalaClasspath(javaTask.getClasspath());

        List<SourceProvider> providers = variantData.getSourceSets();

        providers.forEach(provider -> {
            if (provider instanceof HasConvention) {
                HasConvention hasConvention = (HasConvention) provider;

                ScalaSourceSet scalaSourceSet = (ScalaSourceSet) hasConvention.getConvention().getPlugins().get("scala");
                if (scalaSourceSet != null) {
                    scalaTask.source(scalaSourceSet.getScala());

                    Set<String> scalaFiles = scalaSourceSet.getScala().getFiles().stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.toSet());

                    javaTask.exclude(scalaFiles);
                }
            }
        });

        if (scalaTask.getSource().isEmpty()) {
            LOGGER.debug("no scala sources found for {} removing scala task", variantName);
            scalaTask.setEnabled(false);
            return;
        }

        LOGGER.debug("scala sources for {}: {}", variantName, scalaTask.getSource().getFiles());

        List<SourceProvider> additionalSourceFiles = variantData.getSourceSets();
        LOGGER.debug("additional source files found at {}", additionalSourceFiles);
        scalaTask.source(additionalSourceFiles);

        scalaTask.doFirst(task -> {
            FileCollection runtimeJars = ImmutableFileCollection
                    .of(androidExtension.getBootClasspath())
                    .plus(javaTask.getClasspath());

            scalaTask.setClasspath(runtimeJars);
            //scalaTask.setScalaClasspath(scalaTask.getClasspath());
            scalaTask.getOptions().setAnnotationProcessorPath(javaTask.getOptions().getAnnotationProcessorPath());
            LOGGER.debug("Java annotationProcessorPath {}", javaTask.getOptions().getAnnotationProcessorPath());
            LOGGER.debug("Scala compiler args {}", scalaTask.getOptions().getCompilerArgs());
        });
        LOGGER.debug("Scala classpath: {}", scalaTask.getClasspath());

        javaTask.finalizedBy(scalaTask);
    }
}
