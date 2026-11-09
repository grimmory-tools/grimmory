package org.booklore.service.metadata.sidecar;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.dto.sidecar.SidecarMetadata;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.ComicCreatorEntity;
import org.booklore.model.entity.ComicCreatorMappingEntity;
import org.booklore.model.entity.ComicMetadataEntity;
import org.booklore.model.enums.ComicCreatorRole;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SidecarMetadataMapperTest {

    private final SidecarMetadataMapper mapper = new SidecarMetadataMapper();

    @Test
    void toSidecarMetadata_comicWithPenciller_includesPenciller() {
        BookMetadataEntity meta = buildComicMetadata(ComicCreatorRole.PENCILLER);

        SidecarMetadata sidecar = mapper.toSidecarMetadata(meta, null);

        ComicMetadata comic = sidecar.getMetadata().getComicMetadata();
        assertEquals(Set.of("Creator PENCILLER"), comic.getPencillers());
        assertEquals(Set.of(), comic.getInkers());
    }

    @Test
    void toSidecarMetadata_comicWithAllRoles_mapsEachRoleToItsField() {
        BookMetadataEntity meta = buildComicMetadata(ComicCreatorRole.values());

        SidecarMetadata sidecar = mapper.toSidecarMetadata(meta, null);

        ComicMetadata comic = sidecar.getMetadata().getComicMetadata();
        assertEquals(Set.of("Creator PENCILLER"), comic.getPencillers());
        assertEquals(Set.of("Creator INKER"), comic.getInkers());
        assertEquals(Set.of("Creator COLORIST"), comic.getColorists());
        assertEquals(Set.of("Creator LETTERER"), comic.getLetterers());
        assertEquals(Set.of("Creator COVER_ARTIST"), comic.getCoverArtists());
        assertEquals(Set.of("Creator EDITOR"), comic.getEditors());
    }

    @Test
    void toBookMetadata_exportedComic_keepsCreators() {
        BookMetadataEntity meta = buildComicMetadata(ComicCreatorRole.PENCILLER);

        BookMetadata imported = mapper.toBookMetadata(mapper.toSidecarMetadata(meta, null));

        assertEquals(Set.of("Creator PENCILLER"), imported.getComicMetadata().getPencillers());
    }

    private BookMetadataEntity buildComicMetadata(ComicCreatorRole... roles) {
        ComicMetadataEntity comic = new ComicMetadataEntity();
        Set<ComicCreatorMappingEntity> creatorMappings = new HashSet<>();
        long id = 1L;
        for (ComicCreatorRole role : roles) {
            ComicCreatorEntity creator = new ComicCreatorEntity();
            creator.setId(id++);
            creator.setName("Creator " + role.name());
            creatorMappings.add(ComicCreatorMappingEntity.builder()
                    .creator(creator)
                    .role(role)
                    .comicMetadata(comic)
                    .build());
        }
        comic.setCreatorMappings(creatorMappings);

        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("Test Comic");
        meta.setComicMetadata(comic);
        return meta;
    }
}
