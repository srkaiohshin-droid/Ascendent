package br.com.ascendant.pack;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Abstraction provider for content pack catalogs (local storage vs remote repository).
 */
public interface IPackCatalogProvider {
    /**
     * Lists all manifests available through this provider.
     */
    List<PackManifest> listAvailablePacks();

    /**
     * Obtains the pack archive for installation into the local packs directory.
     */
    Optional<Path> fetchPack(String packId, Path destinationDirectory);
}
