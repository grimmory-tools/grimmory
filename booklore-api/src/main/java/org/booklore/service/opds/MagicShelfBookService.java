package org.booklore.service.opds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.GroupRule;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.MagicShelfEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.MagicShelfRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.BookRuleEvaluatorService;
import org.booklore.service.restriction.ContentRestrictionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class MagicShelfBookService {

    private final MagicShelfRepository magicShelfRepository;
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;
    private final BookRuleEvaluatorService ruleEvaluatorService;
    private final ContentRestrictionService contentRestrictionService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<Book> getBooksByMagicShelfId(Long userId, Long magicShelfId, int page, int size) {
        MagicShelfEntity shelf = validateMagicShelfAccess(userId, magicShelfId);
        try {
            GroupRule groupRule = objectMapper.readValue(shelf.getFilterJson(), GroupRule.class);
            Specification<BookEntity> specification = ruleEvaluatorService.toSpecification(groupRule, userId);
            specification = specification.and(createLibraryFilterSpecification(userId));
            Pageable pageable = PageRequest.of(Math.max(page, 0), size);

            Page<BookEntity> booksPage = bookRepository.findAll(specification, pageable);

            List<BookEntity> filteredEntities = contentRestrictionService.applyRestrictions(booksPage.getContent(), userId);
            List<Book> books = filteredEntities.stream()
                    .map(bookMapper::toBook)
                    .map(book -> filterBook(book, userId))
                    .toList();
            return new PageImpl<>(books, pageable, booksPage.getTotalElements());
        } catch (Exception e) {
            log.error("Failed to parse or execute magic shelf rules", e);
            throw new RuntimeException("Failed to parse or execute magic shelf rules: " + e.getMessage(), e);
        }
    }

    public List<Long> getBookIdsByMagicShelfId(Long userId, Long magicShelfId) {
        return getBookIdsByMagicShelfId(userId, magicShelfId, Integer.MAX_VALUE);
    }

    public List<Long> getBookIdsByMagicShelfId(Long userId, Long magicShelfId, int limit) {
        MagicShelfEntity shelf = validateMagicShelfAccess(userId, magicShelfId);
        try {
            GroupRule groupRule = objectMapper.readValue(shelf.getFilterJson(), GroupRule.class);
            Specification<BookEntity> specification = ruleEvaluatorService.toSpecification(groupRule, userId);
            specification = specification.and(createLibraryFilterSpecification(userId));

            Pageable pageable = PageRequest.of(0, limit);
            Page<BookEntity> booksPage = bookRepository.findAll(specification, pageable);
            List<BookEntity> filtered = contentRestrictionService.applyRestrictions(booksPage.getContent(), userId);
            return filtered.stream().map(BookEntity::getId).toList();
        } catch (Exception e) {
            log.error("Failed to parse or execute magic shelf rules", e);
            throw new RuntimeException("Failed to parse or execute magic shelf rules: " + e.getMessage(), e);
        }
    }

    public String getMagicShelfName(Long magicShelfId) {
        return magicShelfRepository.findById(magicShelfId)
                .map(s -> s.getName() + " - Magic Shelf")
                .orElse("Magic Shelf Books");
    }

    private MagicShelfEntity validateMagicShelfAccess(Long userId, Long magicShelfId) {
        MagicShelfEntity shelf = magicShelfRepository.findById(magicShelfId)
                .orElseThrow(() -> ApiError.MAGIC_SHELF_NOT_FOUND.createException(magicShelfId));

        if (userId == null) {
            if (!shelf.isPublic()) {
                throw ApiError.FORBIDDEN.createException("You are not allowed to access this magic shelf");
            }
            return shelf;
        }

        BookLoreUserEntity entity = userRepository.findByIdWithDetails(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        if (entity.getPermissions() == null ||
                (!entity.getPermissions().isPermissionAccessOpds() && !entity.getPermissions().isPermissionAdmin())) {
            throw ApiError.FORBIDDEN.createException("You are not allowed to access this resource");
        }

        boolean isOwner = shelf.getUserId().equals(userId);
        boolean isPublic = shelf.isPublic();
        boolean isAdmin = entity.getPermissions().isPermissionAdmin();

        if (!isOwner && !isPublic && !isAdmin) {
            throw ApiError.FORBIDDEN.createException("You are not allowed to access this magic shelf");
        }

        return shelf;
    }

    private Specification<BookEntity> createLibraryFilterSpecification(Long userId) {
        return (root, query, cb) -> {
            BookLoreUserEntity entity = userRepository.findByIdWithDetails(userId)
                    .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

            BookLoreUser user = bookLoreUserTransformer.toDTO(entity);

            if (user.getPermissions() != null && user.getPermissions().isAdmin()) {
                return cb.conjunction();
            }

            Set<Long> userLibraryIds = user.getAssignedLibraries().stream()
                    .map(Library::getId)
                    .collect(Collectors.toSet());

            return root.get("library").get("id").in(userLibraryIds);
        };
    }

    private Book filterBook(Book dto, Long userId) {
        if (dto.getShelves() != null && userId != null) {
            dto.setShelves(dto.getShelves().stream()
                    .filter(shelf -> userId.equals(shelf.getUserId()))
                    .collect(Collectors.toSet()));
        }
        return dto;
    }
}
