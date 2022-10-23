package io.github.dsvdsv.scala.android.plugin;

import org.gradle.api.JavaVersion;

import java.util.List;

class TargetVersionDetector {
    static JavaVersion javaVersion(List<String> additionalParameters) {
        return additionalParameters.stream().filter(s -> s.contains("target:"))
                .map(TargetVersionDetector::detect)
                .findFirst().orElse(JavaVersion.VERSION_1_8);
    }

    private static JavaVersion detect(String target) {
        var splitVal = target.split(":");
        if (splitVal.length < 2) {
            return JavaVersion.VERSION_1_8;
        } else {
            var ver = splitVal[1];
            return JavaVersion.toVersion(ver);
        }
    }
}
