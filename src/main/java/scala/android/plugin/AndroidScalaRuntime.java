package scala.android.plugin;

import org.gradle.api.Project;
import org.gradle.api.tasks.ScalaRuntime;

import javax.annotation.Nullable;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidScalaRuntime extends ScalaRuntime {

    private static final Pattern SCALA_JAR_PATTERN = Pattern.compile("^.*?scala-(\\w.*?)-(\\d.*).jar");

    public AndroidScalaRuntime(Project project) {
        super(project);
    }

    @Override
    @Nullable
    public String getScalaVersion(File scalaJar) {
        Matcher matcher = SCALA_JAR_PATTERN.matcher(scalaJar.getName());
        return matcher.matches() ? matcher.group(2) : null;
    }

    @Override
    @Nullable
    public File findScalaJar(Iterable<File> classpath, String appendix) {
        for (File file : classpath) {
            Matcher matcher = SCALA_JAR_PATTERN.matcher(file.getName());
            if (matcher.matches() && matcher.group(1).equals(appendix)) {
                return file;
            }
        }
        return null;
    }
}
