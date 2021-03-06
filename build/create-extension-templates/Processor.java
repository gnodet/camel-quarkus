package [=javaPackageBase].deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class [=artifactIdBaseCamelCase]Processor {

    private static final String FEATURE = "camel-[=artifactIdBase]";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

}
