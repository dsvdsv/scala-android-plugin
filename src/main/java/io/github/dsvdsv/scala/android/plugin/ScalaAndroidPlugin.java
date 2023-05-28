package io.github.dsvdsv.scala.android.plugin;

import com.android.build.gradle.*;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.SourceKind;
import com.android.build.gradle.internal.plugins.BasePlugin;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.*;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.tasks.DefaultScalaSourceSet;
import org.gradle.api.internal.tasks.scala.DefaultScalaPluginExtension;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension;
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.plugins.scala.ScalaPluginExtension;
import org.gradle.api.tasks.ScalaRuntime;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.scala.ScalaCompileOptions;
import org.gradle.internal.logging.util.Log4jBannedVersion;
import org.gradle.language.scala.tasks.KeepAliveMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;


public class ScalaAndroidPlugin implements Plugin<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScalaAndroidPlugin.class);

    private static final String DEFAULT_SCALA_ZINC_VERSION = "2.13";

    private static final List<String> ANDROID_PLUGIN_NAMES = Arrays.asList(
            "com.android.internal.application", "com.android.internal.library",
            "com.android.internal.test"
    );

    private final ObjectFactory objectFactory;
    private final JvmEcosystemUtilities jvmEcosystemUtilities;

    @Inject
    public ScalaAndroidPlugin(ObjectFactory objectFactory, JvmEcosystemUtilities jvmEcosystemUtilities) {
        this.objectFactory = objectFactory;
        this.jvmEcosystemUtilities = jvmEcosystemUtilities;
    }

    public void apply(Project project) {
        var scalaRuntime = project.getExtensions().create("scalaRuntime", AndroidScalaRuntime.class, project);

        var scalaPluginExtension = project.getExtensions().create(ScalaPluginExtension.class, "scala", DefaultScalaPluginExtension.class);
        var incrementalAnalysisUsage = objectFactory.named(Usage.class, "incremental-analysis");
        Category incrementalAnalysisCategory = objectFactory.named(Category.class, "scala-analysis");
        configureConfigurations(project, incrementalAnalysisCategory, incrementalAnalysisUsage, scalaPluginExtension);
        configureCompileDefaults(project, scalaRuntime, (DefaultJavaPluginExtension) extensionOf(project, JavaPluginExtension.class));

        var androidPlugin = findBasePlugin(project.getPlugins());

        LOGGER.debug("Found Plugin: {}", androidPlugin);

        var androidExt = (BaseExtension) project.getExtensions().getByName("android");

        androidExt.getSourceSets().all(sourceSet -> {
            if (sourceSet instanceof HasConvention) {
                var sourceSetName = sourceSet.getName();
                var sourceSetPath = project.file("src/" + sourceSetName + "/scala");

                if (!sourceSetPath.exists()) {
                    LOGGER.debug("SourceSet path does not exists for {} {}", sourceSet.getName(), sourceSetPath);
                    return;
                }

                sourceSet.getJava().srcDir(sourceSetPath);

                var scalaSourceSet = new DefaultScalaSourceSet(sourceSetName, objectFactory) {};
                ((HasConvention) sourceSet).getConvention().getPlugins().put("scala", scalaSourceSet);

                var scalaDirectorySet = scalaSourceSet.getScala();
                scalaDirectorySet.srcDir(sourceSetPath);

                LOGGER.debug("Created scala sourceDirectorySet at {}", scalaDirectorySet.getSrcDirs());
            }
        });

        project.afterEvaluate(p -> {
            forEachVariant(androidExt, variant -> processVariant(variant, project, scalaRuntime, androidExt));
            TaskContainer tasks = project.getTasks();
            dependsOnIfPresent(tasks, "compileDebugUnitTestScalaWithScalac", "compileDebugScalaWithScalac");
            dependsOnIfPresent(tasks, "compileReleaseUnitTestScalaWithScalac", "compileReleaseScalaWithScalac");
        });
    }

    private static void dependsOnIfPresent(TaskContainer tasks, String taskName1, String taskName2) {
        dependsOnIfPresent(tasks, taskName1, tasks.findByPath(taskName2));
    }
    private static void dependsOnIfPresent(TaskContainer tasks, String taskName, Task scalaTask) {
        Task task = tasks.findByName(taskName);
        if(task != null) {
            task.dependsOn(scalaTask);
        }
    }
    private static BasePlugin findBasePlugin(PluginContainer plugins) {
        Plugin plugin = ANDROID_PLUGIN_NAMES.stream()
            .map(plugins::findPlugin)
            .findFirst().orElse(null);

        if (plugin instanceof BasePlugin) {
            return (BasePlugin) plugin;
        } else {
            throw new GradleException("You must apply the Android plugin or the Android library plugin before using the scala-android plugin");
        }
    }

    private static void forEachVariant(BaseExtension androidExtension, Consumer<BaseVariant> action) {
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
            ScalaRuntime scalaRuntime,
            BaseExtension androidExtension
    ) {
        var variantName = variantData.getName();
        LOGGER.debug("Processing variant {}", variantName);

        var javaTask = variantData.getJavaCompileProvider().getOrNull();
        if (javaTask == null) {
            LOGGER.info("javaTask it null for {}", variantName);
            return;
        }

        TaskContainer tasks = project.getTasks();
        var taskName = javaTask.getName().replace("Java", "Scala");
        var scalaTask = tasks.create(taskName, ScalaCompile.class);

        scalaTask.getDestinationDirectory().set(javaTask.getDestinationDirectory());
        scalaTask.setClasspath(javaTask.getClasspath());
        scalaTask.dependsOn(javaTask.getDependsOn());
        scalaTask.setScalaClasspath(scalaRuntime.inferScalaClasspath(javaTask.getClasspath()));
        scalaTask.getScalaCompileOptions().getKeepAliveMode().set(KeepAliveMode.SESSION);
        configureCompileOptions(scalaTask.getScalaCompileOptions(), androidExtension);

        var zinc = project.getConfigurations().getByName("zinc");
        var plugins = project.getConfigurations().getByName(ScalaBasePlugin.SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME);

        scalaTask.setScalaCompilerPlugins(plugins.getAsFileTree());
        scalaTask.setZincClasspath(zinc.getAsFileTree());

        LOGGER.debug("scala sources for {}: {}", variantName, scalaTask.getSource().getFiles());

        var additionalSourceFiles = variantData.getSourceFolders(SourceKind.JAVA)
                .stream()
                .map(ConfigurableFileTree::getDir)
                .toArray();

        LOGGER.debug("additional source files found at {}", additionalSourceFiles);

        var providers = variantData.getSourceSets();

        providers.forEach(provider -> {
            if (provider instanceof HasConvention) {
                var hasConvention = (HasConvention) provider;

                var scalaSourceSet = (ScalaSourceSet) hasConvention.getConvention().getPlugins().get("scala");
                if (scalaSourceSet != null) {
                    SourceDirectorySet srcDirSet = scalaSourceSet.getScala();
                    var allFiles = srcDirSet.plus(project.getLayout().files(additionalSourceFiles));
                    scalaTask.setSource(allFiles);

                    var scalaFiles = srcDirSet.getFiles().stream()
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

        scalaTask.doFirst(task -> {
            var runtimeJars =
                    project.getLayout().files(androidExtension.getBootClasspath().toArray())
                            .plus(javaTask.getClasspath());

            scalaTask.setClasspath(runtimeJars);
            scalaTask.getOptions().setAnnotationProcessorPath(javaTask.getOptions().getAnnotationProcessorPath());

            var incrementalOptions = scalaTask.getScalaCompileOptions().getIncrementalOptions();
            incrementalOptions.getAnalysisFile().set(
                    project.getLayout().getBuildDirectory().file("tmp/scala/compilerAnalysis/" + scalaTask.getName() + ".analysis")
            );

            incrementalOptions.getClassfileBackupDir().set(
                    project.getLayout().getBuildDirectory().file("tmp/scala/classfileBackup/" + scalaTask.getName() + ".bak")
            );

            LOGGER.debug("Java annotationProcessorPath {}", javaTask.getOptions().getAnnotationProcessorPath());
            LOGGER.debug("Scala compiler args {}", scalaTask.getOptions().getCompilerArgs());
        });
        LOGGER.debug("Scala classpath: {}", scalaTask.getClasspath());

        javaTask.finalizedBy(scalaTask);

        // Prevent error from implicit dependency (AGP 8.0 or above)
        // https://docs.gradle.org/8.1.1/userguide/validation_problems.html#implicit_dependency
        String capitalizedName = variantName.substring(0,1).toUpperCase() + variantName.substring(1);
        dependsOnIfPresent(tasks, "process" + capitalizedName + "JavaRes", scalaTask);
        dependsOnIfPresent(tasks, "merge" + capitalizedName + "JavaResource", scalaTask);
        dependsOnIfPresent(tasks, "dexBuilder" + capitalizedName, scalaTask);
        dependsOnIfPresent(tasks, "transform" + capitalizedName + "ClassesWithAsm", scalaTask);
        dependsOnIfPresent(tasks, "lintVitalAnalyze" + capitalizedName, scalaTask);
    }

    private static void configureCompileOptions(ScalaCompileOptions scalaCompileOptions, BaseExtension androidExtension) {
        var compileOptions = androidExtension.getCompileOptions();

        var javaVersion = TargetVersionDetector.javaVersion(scalaCompileOptions.getAdditionalParameters());
        LOGGER.info("Detect target platform version {}", javaVersion);

        if (compileOptions.getTargetCompatibility().compareTo(javaVersion) < 0) {
            compileOptions.setTargetCompatibility(javaVersion);
        }

        if (compileOptions.getSourceCompatibility().compareTo(javaVersion) < 0) {
            compileOptions.setSourceCompatibility(javaVersion);
        }
    }

    private static void configureCompileDefaults(final Project project, final ScalaRuntime scalaRuntime, final DefaultJavaPluginExtension javaExtension) {
        project.getTasks().withType(ScalaCompile.class).configureEach(compile -> {
            ConventionMapping conventionMapping = compile.getConventionMapping();
            conventionMapping.map("scalaClasspath", (Callable<FileCollection>) () -> scalaRuntime.inferScalaClasspath(compile.getClasspath()));
            conventionMapping.map("zincClasspath", (Callable<Configuration>) () -> project.getConfigurations().getAt(ScalaBasePlugin.ZINC_CONFIGURATION_NAME));
            conventionMapping.map("scalaCompilerPlugins", (Callable<FileCollection>) () -> project.getConfigurations().getAt(ScalaBasePlugin.SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME));
            conventionMapping.map("sourceCompatibility", () -> computeJavaSourceCompatibilityConvention(javaExtension, compile).toString());
            conventionMapping.map("targetCompatibility", () -> computeJavaTargetCompatibilityConvention(javaExtension, compile).toString());
            compile.getScalaCompileOptions().getKeepAliveMode().convention(KeepAliveMode.SESSION);
        });
    }

    private static JavaVersion computeJavaSourceCompatibilityConvention(DefaultJavaPluginExtension javaExtension, ScalaCompile compileTask) {
        JavaVersion rawSourceCompatibility = javaExtension.getRawSourceCompatibility();
        if (rawSourceCompatibility != null) {
            return rawSourceCompatibility;
        }
        return JavaVersion.toVersion(compileTask.getJavaLauncher().get().getMetadata().getLanguageVersion().toString());
    }

    private static JavaVersion computeJavaTargetCompatibilityConvention(DefaultJavaPluginExtension javaExtension, ScalaCompile compileTask) {
        JavaVersion rawTargetCompatibility = javaExtension.getRawTargetCompatibility();
        if (rawTargetCompatibility != null) {
            return rawTargetCompatibility;
        }
        return JavaVersion.toVersion(compileTask.getSourceCompatibility());
    }

    private void configureConfigurations(final Project project, Category incrementalAnalysisCategory, final Usage incrementalAnalysisUsage, ScalaPluginExtension scalaPluginExtension) {
        DependencyHandler dependencyHandler = project.getDependencies();

        ConfigurationInternal plugins = (ConfigurationInternal) project.getConfigurations().create(ScalaBasePlugin.SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME);
        plugins.setTransitive(false);
        plugins.setCanBeConsumed(false);
        jvmEcosystemUtilities.configureAsRuntimeClasspath(plugins);

        Configuration zinc = project.getConfigurations().create("zinc");
        zinc.setVisible(false);
        zinc.setDescription("The Zinc incremental compiler to be used for this Scala project.");
        zinc.setCanBeConsumed(false);

        zinc.getResolutionStrategy().eachDependency(rule -> {
            if (rule.getRequested().getGroup().equals("com.typesafe.zinc") && rule.getRequested().getName().equals("zinc")) {
                rule.useTarget("org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + ScalaBasePlugin.DEFAULT_ZINC_VERSION);
                rule.because("Typesafe Zinc is no longer maintained.");
            }
        });

        zinc.defaultDependencies(dependencies -> {
            dependencies.add(dependencyHandler.create("org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + scalaPluginExtension.getZincVersion().get()));
            // Add safeguard and clear error if the user changed the scala version when using default zinc
            zinc.getIncoming().afterResolve(resolvableDependencies -> resolvableDependencies.getResolutionResult().allComponents(component -> {
                if (component.getModuleVersion() != null && component.getModuleVersion().getName().equals("scala-library")) {
                    if (!component.getModuleVersion().getVersion().startsWith(DEFAULT_SCALA_ZINC_VERSION)) {
                        throw new InvalidUserCodeException("The version of 'scala-library' was changed while using the default Zinc version. " +
                                "Version " + component.getModuleVersion().getVersion() + " is not compatible with org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + ScalaBasePlugin.DEFAULT_ZINC_VERSION);
                    }
                }
            }));
        });
        zinc.getDependencyConstraints().add(dependencyHandler.getConstraints().create(Log4jBannedVersion.LOG4J2_CORE_COORDINATES, constraint -> constraint.version(version -> {
            version.require(Log4jBannedVersion.LOG4J2_CORE_REQUIRED_VERSION);
            version.reject(Log4jBannedVersion.LOG4J2_CORE_VULNERABLE_VERSION_RANGE);
        })));

        final Configuration incrementalAnalysisElements = project.getConfigurations().create("incrementalScalaAnalysisElements");
        incrementalAnalysisElements.setVisible(false);
        incrementalAnalysisElements.setDescription("Incremental compilation analysis files");
        incrementalAnalysisElements.setCanBeResolved(false);
        incrementalAnalysisElements.setCanBeConsumed(true);
        incrementalAnalysisElements.getAttributes().attribute(CATEGORY_ATTRIBUTE, incrementalAnalysisCategory);
        incrementalAnalysisElements.getAttributes().attribute(USAGE_ATTRIBUTE, incrementalAnalysisUsage);

        AttributeMatchingStrategy<Usage> matchingStrategy = dependencyHandler.getAttributesSchema().attribute(USAGE_ATTRIBUTE);
        matchingStrategy.getDisambiguationRules().add(UsageDisambiguationRules.class, actionConfiguration -> {
            actionConfiguration.params(incrementalAnalysisUsage);
            actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_API));
            actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        });
    }

    private static <T> T extensionOf(ExtensionAware extensionAware, Class<T> type) {
        return extensionAware.getExtensions().getByType(type);
    }

}
