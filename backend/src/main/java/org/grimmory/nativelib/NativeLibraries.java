package org.grimmory.nativelib;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JVM-wide single source of truth for native-library availability.
 *
 * <p>Initialization is intentionally serialized via the holder idiom so native
 * library loading is forced exactly once under the JVM class-init lock.
 */
@Slf4j
public final class NativeLibraries {

    public enum Library {
        PDFIUM,
        LIBARCHIVE,
        EPUB4J_NATIVE
    }

    private static final Map<Library, Probe> PROBES;

    static {
        Map<Library, Probe> probes = new LinkedHashMap<>();

        probes.put(Library.PDFIUM, new Probe("PDFium", () -> {
            Boolean clean = tryInvokeStaticBoolean("org.grimmory.pdfium4j.PdfiumLibrary");
            if (clean != null) {
                return clean;
            }
            org.grimmory.pdfium4j.PdfiumLibrary.initialize();
            return true;
        }));

        probes.put(Library.LIBARCHIVE, new Probe("libarchive", com.github.gotson.nightcompress.Archive::isAvailable));

        probes.put(Library.EPUB4J_NATIVE, new Probe("epub4j-native", () -> {
            Boolean clean = tryInvokeStaticBoolean(
                    "org.grimmory.epub4j.native_parsing.EpubNativeLibrary"
            );
            if (clean != null) {
                return clean;
            }
            Class.forName(
                    "org.grimmory.epub4j.native_parsing.PanamaConstants",
                    true,
                    NativeLibraries.class.getClassLoader()
            );
            return true;
        }));

        PROBES = Collections.unmodifiableMap(probes);
    }

    private final Map<Library, Boolean> status;

    private NativeLibraries() {
        Map<Library, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<Library, Probe> entry : PROBES.entrySet()) {
            result.put(entry.getKey(), runProbe(entry.getValue()));
        }
        this.status = Collections.unmodifiableMap(result);
        logSummary();
    }

    private static boolean runProbe(Probe probe) {
        try {
            return probe.fn.get();
        } catch (Throwable t) {
            log.warn("{} native library unavailable: {}", probe.name, t.toString());
            return false;
        }
    }

    private static Boolean tryInvokeStaticBoolean(String fqcn) throws Throwable {
        Class<?> cls;
        try {
            cls = Class.forName(fqcn, false, NativeLibraries.class.getClassLoader());
        } catch (ClassNotFoundException _) {
            return null;
        }

        Method method;
        try {
            method = cls.getMethod("isAvailable");
        } catch (NoSuchMethodException _) {
            return null;
        }

        if (method.getReturnType() != boolean.class) {
            return null;
        }

        Class.forName(fqcn, true, NativeLibraries.class.getClassLoader());
        try {
            return (Boolean) method.invoke(null);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            throw cause != null ? cause : ite;
        }
    }

    private void logSummary() {
        StringBuilder sb = new StringBuilder("Native libraries,");
        boolean first = true;
        for (Map.Entry<Library, Boolean> entry : status.entrySet()) {
            sb.append(first ? " " : ", ")
                    .append(PROBES.get(entry.getKey()).name)
                    .append(": ")
                    .append(entry.getValue() ? "loaded" : "NOT available");
            first = false;
        }
        log.info(sb.toString());
    }

    private static final class Holder {
        static final NativeLibraries INSTANCE = new NativeLibraries();
    }

    public static NativeLibraries get() {
        return Holder.INSTANCE;
    }

    public static void ensureInitialized() {
        NativeLibraries ignored = Holder.INSTANCE;
    }

    public boolean isAvailable(Library library) {
        return Boolean.TRUE.equals(status.get(library));
    }

    public boolean isPdfiumAvailable() {
        return isAvailable(Library.PDFIUM);
    }

    public boolean isLibArchiveAvailable() {
        return isAvailable(Library.LIBARCHIVE);
    }

    public boolean isEpubNativeAvailable() {
        return isAvailable(Library.EPUB4J_NATIVE);
    }

    private record Probe(String name, CheckedBooleanSupplier fn) {}

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean get() throws Throwable;
    }
}
