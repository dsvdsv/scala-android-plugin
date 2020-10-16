package scala.android.plugin;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;

import javax.inject.Inject;

class UsageDisambiguationRules implements AttributeDisambiguationRule<Usage> {
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
