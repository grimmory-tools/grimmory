package org.booklore.nativelib;

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

    public boolean isGumboAvailable() {
        return libs.isGumboAvailable();
    }

    public boolean isImageCodecAvailable() {
        return libs.isImageCodecAvailable();
    }

    public boolean isPugixmlAvailable() {
        return libs.isPugixmlAvailable();
    }

    public boolean isUchardetAvailable() {
        return libs.isUchardetAvailable();
    }

    public boolean isAvailable(NativeLibraries.Library library) {
        return libs.isAvailable(library);
    }
}
