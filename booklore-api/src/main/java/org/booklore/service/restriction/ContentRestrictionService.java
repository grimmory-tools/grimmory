package org.booklore.service.restriction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.ContentRestriction;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.UserContentRestrictionEntity;
import org.booklore.model.enums.ContentRestrictionMode;
import org.booklore.model.enums.ContentRestrictionType;
import org.booklore.repository.UserContentRestrictionRepository;
import org.booklore.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentRestrictionService {

    private final UserContentRestrictionRepository restrictionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ContentRestriction> getUserRestrictions(Long userId) {
        return restrictionRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ContentRestriction getRestriction(Long restrictionId) {
        return restrictionRepository.findById(restrictionId)
                .map(this::toDto)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Content restriction not found"));
    }

    @Transactional
    public ContentRestriction addRestriction(Long userId, ContentRestriction restriction) {
        BookLoreUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        if (restrictionRepository.existsByUserIdAndRestrictionTypeAndValue(
                userId, restriction.getRestrictionType(), restriction.getValue())) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Restriction already exists");
        }

        UserContentRestrictionEntity entity = UserContentRestrictionEntity.builder()
                .user(user)
                .restrictionType(restriction.getRestrictionType())
                .mode(restriction.getMode())
                .value(restriction.getValue())
                .build();

        return toDto(restrictionRepository.save(entity));
    }

    @Transactional
    public List<ContentRestriction> updateRestrictions(Long userId, List<ContentRestriction> restrictions) {
        BookLoreUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        restrictionRepository.deleteByUserId(userId);

        List<UserContentRestrictionEntity> entities = restrictions.stream()
                .map(r -> UserContentRestrictionEntity.builder()
                        .user(user)
                        .restrictionType(r.getRestrictionType())
                        .mode(r.getMode())
                        .value(r.getValue())
                        .build())
                .collect(Collectors.toList());

        return restrictionRepository.saveAll(entities).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteRestriction(Long restrictionId) {
        if (!restrictionRepository.existsById(restrictionId)) {
            throw ApiError.GENERIC_NOT_FOUND.createException("Content restriction not found");
        }
        restrictionRepository.deleteById(restrictionId);
    }

    @Transactional
    public void deleteAllUserRestrictions(Long userId) {
        restrictionRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<BookEntity> applyRestrictions(List<BookEntity> books, Long userId) {
        List<UserContentRestrictionEntity> restrictions = restrictionRepository.findByUserId(userId);

        if (restrictions.isEmpty()) {
            return books;
        }

        Set<String> excludedCategories = getValuesForTypeAndMode(restrictions, ContentRestrictionType.CATEGORY, ContentRestrictionMode.EXCLUDE);
        Set<String> excludedTags = getValuesForTypeAndMode(restrictions, ContentRestrictionType.TAG, ContentRestrictionMode.EXCLUDE);
        Set<String> excludedMoods = getValuesForTypeAndMode(restrictions, ContentRestrictionType.MOOD, ContentRestrictionMode.EXCLUDE);
        Set<String> excludedContentRatings = getValuesForTypeAndMode(restrictions, ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.EXCLUDE);

        Set<String> allowedCategories = getValuesForTypeAndMode(restrictions, ContentRestrictionType.CATEGORY, ContentRestrictionMode.ALLOW_ONLY);
        Set<String> allowedTags = getValuesForTypeAndMode(restrictions, ContentRestrictionType.TAG, ContentRestrictionMode.ALLOW_ONLY);
        Set<String> allowedMoods = getValuesForTypeAndMode(restrictions, ContentRestrictionType.MOOD, ContentRestrictionMode.ALLOW_ONLY);
        Set<String> allowedContentRatings = getValuesForTypeAndMode(restrictions, ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.ALLOW_ONLY);

        Integer maxAgeRating = getMaxAgeRating(restrictions);

        return books.stream()
                .filter(book -> !hasExcludedContent(book, excludedCategories, excludedTags, excludedMoods, excludedContentRatings))
                .filter(book -> matchesAllowList(book, allowedCategories, allowedTags, allowedMoods, allowedContentRatings))
                .filter(book -> isWithinAgeRating(book, maxAgeRating))
                .collect(Collectors.toList());
    }

    private Set<String> getValuesForTypeAndMode(List<UserContentRestrictionEntity> restrictions,
                                                 ContentRestrictionType type,
                                                 ContentRestrictionMode mode) {
        return restrictions.stream()
                .filter(r -> r.getRestrictionType() == type && r.getMode() == mode)
                .map(r -> r.getValue().toLowerCase())
                .collect(Collectors.toSet());
    }

    private Integer getMaxAgeRating(List<UserContentRestrictionEntity> restrictions) {
        return restrictions.stream()
                .filter(r -> r.getRestrictionType() == ContentRestrictionType.AGE_RATING)
                .filter(r -> r.getMode() == ContentRestrictionMode.EXCLUDE)
                .map(r -> {
                    try {
                        return Integer.parseInt(r.getValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private boolean hasExcludedContent(BookEntity book,
                                       Set<String> excludedCategories,
                                       Set<String> excludedTags,
                                       Set<String> excludedMoods,
                                       Set<String> excludedContentRatings) {
        BookMetadataEntity metadata = book.getMetadata();
        if (metadata == null) {
            return false;
        }

        if (!excludedCategories.isEmpty() && metadata.getCategories() != null) {
            boolean hasExcludedCategory = metadata.getCategories().stream()
                    .anyMatch(c -> excludedCategories.contains(c.getName().toLowerCase()));
            if (hasExcludedCategory) {
                return true;
            }
        }

        if (!excludedTags.isEmpty() && metadata.getTags() != null) {
            boolean hasExcludedTag = metadata.getTags().stream()
                    .anyMatch(t -> excludedTags.contains(t.getName().toLowerCase()));
            if (hasExcludedTag) {
                return true;
            }
        }

        if (!excludedMoods.isEmpty() && metadata.getMoods() != null) {
            boolean hasExcludedMood = metadata.getMoods().stream()
                    .anyMatch(m -> excludedMoods.contains(m.getName().toLowerCase()));
            if (hasExcludedMood) {
                return true;
            }
        }

        if (!excludedContentRatings.isEmpty() && metadata.getContentRating() != null) {
            if (excludedContentRatings.contains(metadata.getContentRating().toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesAllowList(BookEntity book,
                                     Set<String> allowedCategories,
                                     Set<String> allowedTags,
                                     Set<String> allowedMoods,
                                     Set<String> allowedContentRatings) {
        BookMetadataEntity metadata = book.getMetadata();

        if (allowedCategories.isEmpty() && allowedTags.isEmpty() && allowedMoods.isEmpty() && allowedContentRatings.isEmpty()) {
            return true;
        }

        if (metadata == null) {
            return false;
        }

        if (!allowedCategories.isEmpty()) {
            if (metadata.getCategories() == null || metadata.getCategories().isEmpty()) {
                return false;
            }
            boolean hasAllowedCategory = metadata.getCategories().stream()
                    .anyMatch(c -> allowedCategories.contains(c.getName().toLowerCase()));
            if (!hasAllowedCategory) {
                return false;
            }
        }

        if (!allowedTags.isEmpty()) {
            if (metadata.getTags() == null || metadata.getTags().isEmpty()) {
                return false;
            }
            boolean hasAllowedTag = metadata.getTags().stream()
                    .anyMatch(t -> allowedTags.contains(t.getName().toLowerCase()));
            if (!hasAllowedTag) {
                return false;
            }
        }

        if (!allowedMoods.isEmpty()) {
            if (metadata.getMoods() == null || metadata.getMoods().isEmpty()) {
                return false;
            }
            boolean hasAllowedMood = metadata.getMoods().stream()
                    .anyMatch(m -> allowedMoods.contains(m.getName().toLowerCase()));
            if (!hasAllowedMood) {
                return false;
            }
        }

        if (!allowedContentRatings.isEmpty()) {
            if (metadata.getContentRating() == null) {
                return false;
            }
            if (!allowedContentRatings.contains(metadata.getContentRating().toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    private boolean isWithinAgeRating(BookEntity book, Integer maxAgeRating) {
        if (maxAgeRating == null) {
            return true;
        }

        BookMetadataEntity metadata = book.getMetadata();
        if (metadata == null || metadata.getAgeRating() == null) {
            return true;
        }

        return metadata.getAgeRating() < maxAgeRating;
    }

    private ContentRestriction toDto(UserContentRestrictionEntity entity) {
        return ContentRestriction.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .restrictionType(entity.getRestrictionType())
                .mode(entity.getMode())
                .value(entity.getValue())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
