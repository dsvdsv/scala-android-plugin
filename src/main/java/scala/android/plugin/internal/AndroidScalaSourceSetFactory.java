package scala.android.plugin.internal;

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.reflect.Instantiator;
import scala.android.plugin.api.AndroidScalaSourceSet;

public class AndroidScalaSourceSetFactory implements NamedDomainObjectFactory<AndroidScalaSourceSet> {

    private final Instantiator instantiator;
    private final ObjectFactory objects;

    public AndroidScalaSourceSetFactory(Instantiator instantiator, ObjectFactory objects) {
        this.instantiator = instantiator;
        this.objects = objects;
    }

    @Override
    public AndroidScalaSourceSet create(String name) {
        return instantiator.newInstance(DefaultAndroidScalaSourceSet.class, name, objects);
    }
}

