package scala.android.plugin.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.util.ConfigureUtil;
import scala.android.plugin.api.AndroidScalaSourceSet;

public class DefaultAndroidScalaSourceSet implements AndroidScalaSourceSet, HasPublicType {

    private final String name;
    private final SourceDirectorySet scala;
    private final SourceDirectorySet allScala;

    public DefaultAndroidScalaSourceSet(String displayName, ObjectFactory objects) {
        this.name = displayName;

        this.scala = objects.sourceDirectorySet(displayName + "scala", displayName + " Scala source");
        this.scala.getFilter().include("**/*.java", "**/*.scala");
        this.allScala = objects.sourceDirectorySet(displayName + "allscala", displayName + " Scala source");
        this.allScala.source(scala);
        this.allScala.getFilter().include("**/*.scala");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SourceDirectorySet getScala() {
        return scala;
    }

    @Override
    public ScalaSourceSet scala(Closure closure) {
        ConfigureUtil.configure(closure, scala);
        return this;
    }

    @Override
    public ScalaSourceSet scala(Action<? super SourceDirectorySet> action) {
        action.execute(scala);
        return this;
    }

    @Override
    public SourceDirectorySet getAllScala() {
        return allScala;
    }

    @Override
    public TypeOf<?> getPublicType() {
        return TypeOf.typeOf(ScalaSourceSet.class);
    }
}
