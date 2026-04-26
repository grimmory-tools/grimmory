package org.grimmory.test;

import org.booklore.nativelib.NativeLibraries;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Enables tests only when epub4j-native binaries are available on the current
 * platform.
 *
 * <p>Delegates to the JVM-wide native-library loader singleton.
 */
public class EpubNativeAvailableCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("epub4j-native library is available");

    private static final ConditionEvaluationResult DISABLED =
            ConditionEvaluationResult.disabled("epub4j-native library not available on this platform");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return NativeLibraries.get().isEpubNativeAvailable() ? ENABLED : DISABLED;
    }
}
