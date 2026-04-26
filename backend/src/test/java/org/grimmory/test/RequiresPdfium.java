package org.grimmory.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

/**
 * Marks a test class or method as requiring PDFium native binaries.
 * Tests annotated with this will be skipped (not failed) if the
 * PDFium native library is not available on the current platform.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(PdfiumAvailableCondition.class)
public @interface RequiresPdfium {
}
