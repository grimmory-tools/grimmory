package org.booklore.mapper;

import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.entity.*;
import org.booklore.model.enums.ComicCreatorRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ComicMetadataMapper {

    @Mapping(target = "characters", source = "characters", qualifiedByName = "charactersToStrings")
    @Mapping(target = "teams", source = "teams", qualifiedByName = "teamsToStrings")
    @Mapping(target = "locations", source = "locations", qualifiedByName = "locationsToStrings")
    @Mapping(target = "pencillers", source = "creatorMappings", qualifiedByName = "pencillersToStrings")
    @Mapping(target = "inkers", source = "creatorMappings", qualifiedByName = "inkersToStrings")
    @Mapping(target = "colorists", source = "creatorMappings", qualifiedByName = "coloristsToStrings")
    @Mapping(target = "letterers", source = "creatorMappings", qualifiedByName = "letterersToStrings")
    @Mapping(target = "coverArtists", source = "creatorMappings", qualifiedByName = "coverArtistsToStrings")
    @Mapping(target = "editors", source = "creatorMappings", qualifiedByName = "editorsToStrings")
    ComicMetadata toComicMetadata(ComicMetadataEntity entity);

    @Named("charactersToStrings")
    default Set<String> charactersToStrings(Set<ComicCharacterEntity> characters) {
        if (characters == null) return Collections.emptySet();
        return characters.stream().map(ComicCharacterEntity::getName).collect(Collectors.toSet());
    }

    @Named("teamsToStrings")
    default Set<String> teamsToStrings(Set<ComicTeamEntity> teams) {
        if (teams == null) return Collections.emptySet();
        return teams.stream().map(ComicTeamEntity::getName).collect(Collectors.toSet());
    }

    @Named("locationsToStrings")
    default Set<String> locationsToStrings(Set<ComicLocationEntity> locations) {
        if (locations == null) return Collections.emptySet();
        return locations.stream().map(ComicLocationEntity::getName).collect(Collectors.toSet());
    }

    @Named("pencillersToStrings")
    default Set<String> pencillersToStrings(Set<ComicCreatorMappingEntity> mappings) {
        return creatorsToStringsByRole(mappings, ComicCreatorRole.PENCILLER);
    }

    @Named("inkersToStrings")
    default Set<String> inkersToStrings(Set<ComicCreatorMappingEntity> mappings) {
        return creatorsToStringsByRole(mappings, ComicCreatorRole.INKER);
    }

    @Named("coloristsToStrings")
    default Set<String> coloristsToStrings(Set<ComicCreatorMappingEntity> mappings) {
        return creatorsToStringsByRole(mappings, ComicCreatorRole.COLORIST);
    }

    @Named("letterersToStrings")
    default Set<String> letterersToStrings(Set<ComicCreatorMappingEntity> mappings) {
        return creatorsToStringsByRole(mappings, ComicCreatorRole.LETTERER);
    }

    @Named("coverArtistsToStrings")
    default Set<String> coverArtistsToStrings(Set<ComicCreatorMappingEntity> mappings) {
        return creatorsToStringsByRole(mappings, ComicCreatorRole.COVER_ARTIST);
    }

    @Named("editorsToStrings")
    default Set<String> editorsToStrings(Set<ComicCreatorMappingEntity> mappings) {
        return creatorsToStringsByRole(mappings, ComicCreatorRole.EDITOR);
    }

    default Set<String> creatorsToStringsByRole(Set<ComicCreatorMappingEntity> mappings, ComicCreatorRole role) {
        if (mappings == null) return Collections.emptySet();
        return mappings.stream()
                .filter(m -> m.getRole() == role)
                .map(m -> m.getCreator().getName())
                .collect(Collectors.toSet());
    }
}
