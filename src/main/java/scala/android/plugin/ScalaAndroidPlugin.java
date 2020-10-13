package scala.android.plugin;

import com.android.build.gradle.*;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.SourceKind;
import com.android.build.gradle.internal.plugins.AppPlugin;
import com.android.build.gradle.internal.plugins.BasePlugin;
import com.android.builder.model.SourceProvider;
import com.google.common.collect.ImmutableSet;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.internal.tasks.DefaultScalaSourceSet;
import org.gradle.api.internal.tasks.scala.DefaultScalaPluginExtension;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.plugins.scala.ScalaPluginExtension;
import org.gradle.api.tasks.ScalaRuntime;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.internal.reflect.Instantiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.android.plugin.api.AndroidScalaSourceSet;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;


public class ScalaAndroidPlugin implements Plugin<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScalaAndroidPlugin.class);

    public static final String DEFAULT_ZINC_VERSION = "1.3.5";
    private static final String DEFAULT_SCALA_ZINC_VERSION = "2.12";

    private static final List<String> ANDROID_PLUGIN_NAMES = Arrays.asList(
            "com.android.internal.application", "com.android.internal.library",
            "com.android.internal.test"
    );

    private final ObjectFactory objectFactory;
    private final Instantiator instantiator;


    @Inject
    public ScalaAndroidPlugin(ObjectFactory objectFactory, Instantiator instantiator) {
        this.objectFactory = objectFactory;
        this.instantiator = instantiator;
    }

    public void apply(Project project) {
        project.getExtensions().create("androidScala", ScalaAndroidExtension.class, project, instantiator);

        ScalaRuntime scalaRuntime = project.getExtensions().create("scalaRuntime", ScalaRuntime.class, project);
        ScalaPluginExtension scalaPluginExtension = project.getExtensions().create(ScalaPluginExtension.class, "scala", DefaultScalaPluginExtension.class);

        Usage incrementalAnalysisUsage = objectFactory.named(Usage.class, "incremental-analysis");
        configureConfigurations(project, incrementalAnalysisUsage, scalaPluginExtension);

        configureCompileDefaults(project, scalaRuntime);

        BasePlugin androidPlugin = (AppPlugin) ANDROID_PLUGIN_NAMES.stream()
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
                LOGGER.debug("SourceSet path does not exists for {} {}", sourceSet.getName(), sourceSetPath);
                return;
            }

            sourceSet.getJava().srcDir(sourceSetPath);

            DefaultScalaSourceSet scalaSourceSet = new DefaultScalaSourceSet(sourceSetName, objectFactory);
            ((HasConvention) sourceSet).getConvention().getPlugins().put("scala", scalaSourceSet);

            SourceDirectorySet scalaDirectorySet = scalaSourceSet.getScala();
            scalaDirectorySet.srcDir(sourceSetPath);

            LOGGER.debug("Created scala sourceDirectorySet at {}", scalaDirectorySet.getSrcDirs());
        });

        project.afterEvaluate(p -> {
            forEachVariant(project, androidExt, variant -> processVariant(variant, project, scalaRuntime, androidExt, scalaAndroidExt));
        });
    }

    private static void configureCompileDefaults(final Project project, final ScalaRuntime scalaRuntime) {
        project.getTasks().withType(ScalaCompile.class).configureEach(new Action<ScalaCompile>() {
            @Override
            public void execute(final ScalaCompile compile) {
                compile.getConventionMapping().map("scalaClasspath", new Callable<FileCollection>() {
                    @Override
                    public FileCollection call() throws Exception {
                        return scalaRuntime.inferScalaClasspath(compile.getClasspath());
                    }
                });
                compile.getConventionMapping().map("zincClasspath", new Callable<Configuration>() {
                    @Override
                    public Configuration call() throws Exception {
                        return project.getConfigurations().getAt("zinc");
                    }
                });
                compile.getConventionMapping().map("scalaCompilerPlugins", new Callable<FileCollection>() {
                    @Override
                    public FileCollection call() throws Exception {
                        return project.getConfigurations().getAt("scalaCompilerPlugins");
                    }
                });
            }
        });
    }

    private void configureConfigurations(final Project project, final Usage incrementalAnalysisUsage, ScalaPluginExtension scalaPluginExtension) {
        DependencyHandler dependencyHandler = project.getDependencies();

        ConfigurationInternal plugins = (ConfigurationInternal) project.getConfigurations().create("scalaCompilerPlugins");
        plugins.setTransitive(false);
        plugins.setCanBeConsumed(false);

        Configuration zinc = project.getConfigurations().create("zinc");
        zinc.setVisible(false);
        zinc.setDescription("The Zinc incremental compiler to be used for this Scala project.");

        zinc.getResolutionStrategy().eachDependency(rule -> {
            if (rule.getRequested().getGroup().equals("com.typesafe.zinc") && rule.getRequested().getName().equals("zinc")) {
                rule.useTarget("org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + DEFAULT_ZINC_VERSION);
                rule.because("Typesafe Zinc is no longer maintained.");
            }
        });

        zinc.defaultDependencies(dependencies -> {
            dependencies.add(dependencyHandler.create("org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + scalaPluginExtension.getZincVersion().get()));
            // Add safeguard and clear error if the user changed the scala version when using default zinc
            zinc.getIncoming().afterResolve(resolvableDependencies -> {
                resolvableDependencies.getResolutionResult().allComponents(component -> {
                    if (component.getModuleVersion() != null && component.getModuleVersion().getName().equals("scala-library")) {
                        if (!component.getModuleVersion().getVersion().startsWith(DEFAULT_SCALA_ZINC_VERSION)) {
                            throw new InvalidUserCodeException("The version of 'scala-library' was changed while using the default Zinc version. " +
                                    "Version " + component.getModuleVersion().getVersion() + " is not compatible with org.scala-sbt:zinc_" + DEFAULT_SCALA_ZINC_VERSION + ":" + DEFAULT_ZINC_VERSION);
                        }
                    }
                });
            });
        });

        final Configuration incrementalAnalysisElements = project.getConfigurations().create("incrementalScalaAnalysisElements");
        incrementalAnalysisElements.setVisible(false);
        incrementalAnalysisElements.setDescription("Incremental compilation analysis files");
        incrementalAnalysisElements.setCanBeResolved(false);
        incrementalAnalysisElements.setCanBeConsumed(true);
        incrementalAnalysisElements.getAttributes().attribute(USAGE_ATTRIBUTE, incrementalAnalysisUsage);

        AttributeMatchingStrategy<Usage> matchingStrategy = dependencyHandler.getAttributesSchema().attribute(USAGE_ATTRIBUTE);
        matchingStrategy.getDisambiguationRules().add(UsageDisambiguationRules.class, actionConfiguration -> {
            actionConfiguration.params(incrementalAnalysisUsage);
            actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_API));
            actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
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
            ScalaRuntime scalaRuntime, BaseExtension androidExtension,
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
        scalaTask.setScalaClasspath(scalaRuntime.inferScalaClasspath(javaTask.getClasspath()));

        Configuration zinc = project.getConfigurations().getByName("zinc");
        Configuration plugins = project.getConfigurations().getByName("scalaCompilerPlugins");

        scalaTask.setScalaCompilerPlugins(plugins.getAsFileTree());
        scalaTask.setZincClasspath(zinc.getAsFileTree());



        LOGGER.debug("scala sources for {}: {}", variantName, scalaTask.getSource().getFiles());

        Object[] additionalSourceFiles = variantData.getSourceFolders(SourceKind.JAVA)
                .stream()
                .map(files -> files.getDir())
                .toArray();

        LOGGER.debug("additional source files found at {}", additionalSourceFiles);

        List<SourceProvider> providers = variantData.getSourceSets();

        providers.forEach(provider -> {
            if (provider instanceof HasConvention) {
                HasConvention hasConvention = (HasConvention) provider;

                ScalaSourceSet scalaSourceSet = (ScalaSourceSet) hasConvention.getConvention().getPlugins().get("scala");
                if (scalaSourceSet != null) {
                    FileCollection allFiles = scalaSourceSet.getScala()
                            .plus(project.getLayout().files(additionalSourceFiles));

                    scalaTask.setSource(allFiles);

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

        scalaTask.doFirst(task -> {
            LOGGER.info("scalaTask.doFirst");
            FileCollection runtimeJars =
                    project.getLayout().files(androidExtension.getBootClasspath().stream().toArray())
                    .plus(javaTask.getClasspath());

            scalaTask.setClasspath(runtimeJars);
            //scalaTask.setScalaClasspath(scalaTask.getClasspath());
            scalaTask.getOptions().setAnnotationProcessorPath(javaTask.getOptions().getAnnotationProcessorPath());

            IncrementalCompileOptions incrementalOptions = scalaTask.getScalaCompileOptions().getIncrementalOptions();
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
    }


    static class UsageDisambiguationRules implements AttributeDisambiguationRule<Usage> {
        private final ImmutableSet<Usage> expectedUsages;
        private final Usage javaRuntime;

        @Inject
        UsageDisambiguationRules(Usage incrementalAnalysis, Usage javaApi, Usage javaRuntime) {
            this.javaRuntime = javaRuntime;
            this.expectedUsages = ImmutableSet.of(incrementalAnalysis, javaApi, javaRuntime);
        }

        @Override
        public void execute(MultipleCandidatesDetails<Usage> details) {
            if (details.getConsumerValue() == null) {
                if (details.getCandidateValues().equals(expectedUsages)) {
                    details.closestMatch(javaRuntime);
                }
            }
        }
    }
}
