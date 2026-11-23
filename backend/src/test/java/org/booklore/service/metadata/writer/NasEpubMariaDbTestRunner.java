package org.booklore.service.metadata.writer;

import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

public final class NasEpubMariaDbTestRunner {
    private NasEpubMariaDbTestRunner() {}

    public static void main(String[] args) {
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(NasEpubMariaDbCoordinatorTest.class),
                        selectMethod(NasEpubAgentTest.class, "coordinatesAndRecoversSyntheticFilesThroughMariaDbAndMtls"),
                        selectMethod(NasEpubAgentTest.class, "cancelsUnfinishedPreparationThroughMariaDbAndMtls"))
                .build());
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        summary.printFailuresTo(new PrintWriter(System.err, true));
        if (summary.getTestsFoundCount() != 10 || summary.getTestsSucceededCount() != 10
                || summary.getTestsFailedCount() != 0 || summary.getTestsSkippedCount() != 0) {
            throw new IllegalStateException("MariaDB coordinator tests did not all succeed");
        }
    }
}
