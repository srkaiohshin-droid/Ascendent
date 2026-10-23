package br.com.ascendant.pack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Enforces strict security boundaries for content packs.
 * Rejects path traversal, executable code, and zip-bomb structures.
 */
public final class PackSecurity {
    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            ".class", ".jar", ".exe", ".bat", ".cmd", ".sh", ".ps1", ".vbs", ".js", ".dll", ".so", ".dylib"
    );
    private static final int MAX_ENTRIES = 5000;
    private static final long MAX_TOTAL_SIZE_BYTES = 200L * 1024L * 1024L; // 200 MB

    private PackSecurity() {
    }

    public static void validateEntryName(String entryName, Path destinationDir) throws SecurityException {
        if (entryName == null || entryName.isBlank()) {
            throw new SecurityException("Pack entry has empty name.");
        }
        if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw new SecurityException("Pack entry violates directory traversal policy: " + entryName);
        }

        String lower = entryName.toLowerCase();
        for (String ext : FORBIDDEN_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                throw new SecurityException("Pack entry contains prohibited executable or script file: " + entryName);
            }
        }

        Path resolved = destinationDir.resolve(entryName).normalize();
        if (!resolved.startsWith(destinationDir.normalize())) {
            throw new SecurityException("Pack entry attempts path escape outside target directory: " + entryName);
        }
    }

    public static void checkLimits(int entryCount, long totalBytesExtracted) throws SecurityException {
        if (entryCount > MAX_ENTRIES) {
            throw new SecurityException("Pack exceeds maximum allowed entry count (" + MAX_ENTRIES + ").");
        }
        if (totalBytesExtracted > MAX_TOTAL_SIZE_BYTES) {
            throw new SecurityException("Pack exceeds maximum allowed total decompressed size (" + MAX_TOTAL_SIZE_BYTES + " bytes).");
        }
    }
}
