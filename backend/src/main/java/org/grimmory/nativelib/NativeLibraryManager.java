package org.grimmory.nativelib;

import org.springframework.stereotype.Component;

/**
 * Spring-managed thin delegate over {@link NativeLibraries}.
 *
 * <p>Constructing this component eagerly triggers JVM-wide native loading
 * through the serialized holder path once per process.
 */
@Component
public class NativeLibraryManager {

    private final NativeLibraries libs = NativeLibraries.get();

    public boolean isPdfiumAvailable() {
        return libs.isPdfiumAvailable();
    }

    public boolean isLibArchiveAvailable() {
        return libs.isLibArchiveAvailable();
    }

    public boolean isEpubNativeAvailable() {
        return libs.isEpubNativeAvailable();
    }

    public boolean isAvailable(NativeLibraries.Library library) {
        return libs.isAvailable(library);
    }
}
