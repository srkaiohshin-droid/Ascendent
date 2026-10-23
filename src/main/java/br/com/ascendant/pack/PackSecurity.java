package br.com.ascendant.pack;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Enforces strict security boundaries for content packs.
 * Rejects path traversal, executable code, and zip-bomb structures.
 */
public final class PackSecurity {
    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            ".class", ".jar", ".exe", ".bat", ".cmd", ".sh", ".ps1", ".vbs", ".js", ".dll", ".so", ".dylib"
    );
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "con", "prn", "aux", "nul", "conin$", "conout$", "clock$"
    );
    static final int MAX_ENTRIES = 5000;
    static final long MAX_TOTAL_SIZE_BYTES = 200L * 1024L * 1024L;
    static final long MAX_MANIFEST_SIZE_BYTES = 1024L * 1024L;

    private PackSecurity() {
    }

    public static void validateEntryName(String entryName, Path destinationDir) {
        resolveEntryPath(entryName, destinationDir);
    }

    public static Path resolveEntryPath(String entryName, Path destinationDir) {
        if (entryName == null || entryName.isBlank() || destinationDir == null) {
            throw new SecurityException("Pack entry has an empty name or destination.");
        }

        String portableName = entryName.replace('\\', '/');
        if (portableName.indexOf('\0') >= 0
                || portableName.startsWith("/")
                || portableName.matches("(?i)^[a-z]:.*")
                || portableName.contains(":")) {
            throw new SecurityException("Pack entry must use a relative path: " + entryName);
        }

        String[] segments = portableName.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.equals(".") || segment.equals("..")
                    || segment.endsWith(".") || segment.endsWith(" ")
                    || isWindowsDeviceName(segment)
                    || (segment.isEmpty() && i != segments.length - 1)) {
                throw new SecurityException("Pack entry violates directory traversal policy: " + entryName);
            }
        }

        String lowerName = portableName.toLowerCase(Locale.ROOT);
        for (String extension : FORBIDDEN_EXTENSIONS) {
            if (lowerName.endsWith(extension)) {
                throw new SecurityException("Pack entry contains prohibited executable or script file: " + entryName);
            }
        }

        Path normalizedDestination = destinationDir.toAbsolutePath().normalize();
        Path resolved = normalizedDestination.resolve(portableName).normalize();
        if (!resolved.startsWith(normalizedDestination) || resolved.equals(normalizedDestination)) {
            throw new SecurityException("Pack entry attempts path escape outside target directory: " + entryName);
        }
        return resolved;
    }

    private static boolean isWindowsDeviceName(String segment) {
        String name = segment.toLowerCase(Locale.ROOT);
        int extensionStart = name.indexOf('.');
        String baseName = extensionStart < 0 ? name : name.substring(0, extensionStart);
        return WINDOWS_RESERVED_NAMES.contains(baseName) || baseName.matches("(com|lpt)[1-9]");
    }

    public static void checkLimits(int entryCount, long totalBytesExtracted) {
        if (entryCount < 0 || entryCount > MAX_ENTRIES) {
            throw new SecurityException("Pack exceeds maximum allowed entry count (" + MAX_ENTRIES + ").");
        }
        if (totalBytesExtracted < 0 || totalBytesExtracted > MAX_TOTAL_SIZE_BYTES) {
            throw new SecurityException("Pack exceeds maximum allowed total decompressed size ("
                    + MAX_TOTAL_SIZE_BYTES + " bytes).");
        }
    }
}
