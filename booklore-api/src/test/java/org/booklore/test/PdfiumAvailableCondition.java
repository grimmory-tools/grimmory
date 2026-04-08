package org.booklore.test;

import org.grimmory.pdfium4j.PdfiumLibrary;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class PdfiumAvailableCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("PDFium native library is available");

    private static final Boolean AVAILABLE = probe();

    private static Boolean probe() {
        try {
            PdfiumLibrary.initialize();
            return true;
        } catch (Exception | UnsatisfiedLinkError e) {
            return false;
        }
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (Boolean.TRUE.equals(AVAILABLE)) {
            return ENABLED;
        }
        return ConditionEvaluationResult.disabled("PDFium native library not available on this platform");
    }
}
