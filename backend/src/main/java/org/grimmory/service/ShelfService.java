package org.grimmory.service;

import lombok.AllArgsConstructor;
import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.exception.ApiError;
import org.grimmory.mapper.BookMapper;
import org.grimmory.mapper.ShelfMapper;
import org.grimmory.model.dto.Book;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.Shelf;
import org.grimmory.model.dto.request.ShelfCreateRequest;
import org.grimmory.model.entity.BookLoreUserEntity;
import org.grimmory.model.entity.ShelfEntity;
import org.grimmory.model.enums.ShelfType;
import org.grimmory.repository.BookRepository;
import org.grimmory.repository.ShelfRepository;
import org.grimmory.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.grimmory.model.enums.AuditAction;
import org.grimmory.service.audit.AuditService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Service
@Transactional(readOnly = true)
public class ShelfService {

    private final ShelfRepository shelfRepository;
    private final BookRepository bookRepository;
    private final ShelfMapper shelfMapper;
    private final BookMapper bookMapper;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public Shelf createShelf(ShelfCreateRequest request) {
        Long userId = getAuthenticatedUserId();
        if (shelfRepository.existsByUserIdAndName(userId, request.getName())) {
            throw ApiError.SHELF_ALREADY_EXISTS.createException(request.getName());
        }
        if (request.isPublicShelf() && !authenticationService.getAuthenticatedUser().getPermissions().isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Only admins can create public shelves");
        }
        ShelfEntity shelfEntity = ShelfEntity.builder()
                .icon(request.getIcon())
                .name(request.getName())
                .iconType(request.getIconType())
                .isPublic(request.isPublicShelf())
                .user(fetchUserEntityById(userId))
                .build();
        Shelf result = shelfMapper.toShelf(shelfRepository.save(shelfEntity));
        auditService.log(AuditAction.SHELF_CREATED, "Shelf", shelfEntity.getId(), "Created shelf: " + request.getName());
        return result;
    }

    @Transactional
    public Shelf updateShelf(Long id, ShelfCreateRequest request) {
        ShelfEntity shelfEntity = findShelfByIdOrThrow(id);
        if (request.isPublicShelf() && !authenticationService.getAuthenticatedUser().getPermissions().isAdmin()) {
            throw new AccessDeniedException("Only admins can update shelves to be public");
        }
        shelfEntity.setName(request.getName());
        shelfEntity.setIcon(request.getIcon());
        shelfEntity.setIconType(request.getIconType());
        shelfEntity.setPublic(request.isPublicShelf());
        Shelf result = shelfMapper.toShelf(shelfRepository.save(shelfEntity));
        auditService.log(AuditAction.SHELF_UPDATED, "Shelf", id, "Updated shelf: " + request.getName());
        return result;
    }

    public List<Shelf> getShelves() {
        Long userId = getAuthenticatedUserId();
        return shelfRepository.findByUserIdOrPublicShelfTrue(userId).stream()
                .map(shelfMapper::toShelf)
                .toList();
    }

    public Shelf getShelf(Long shelfId) {
        return shelfMapper.toShelf(findShelfByIdOrThrow(shelfId));
    }

    @Transactional
    public void deleteShelf(Long shelfId) {
        shelfRepository.deleteById(shelfId);
        auditService.log(AuditAction.SHELF_DELETED, "Shelf", shelfId, "Deleted shelf: " + shelfId);
    }

    public Shelf getUserKoboShelf() {
        Long userId = getAuthenticatedUserId();
        Optional<ShelfEntity> koboShelf = shelfRepository.findByUserIdAndName(userId, ShelfType.KOBO.getName());
        return koboShelf.map(shelfMapper::toShelf).orElse(null);
    }

    public List<Book> getShelfBooks(Long shelfId) {
        findShelfByIdOrThrow(shelfId);
        return bookRepository.findAllWithMetadataByShelfId(shelfId).stream()
                .map(bookMapper::toBook)
                .toList();
    }

    private Long getAuthenticatedUserId() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        return user.getId();
    }

    private BookLoreUserEntity fetchUserEntityById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID " + userId));
    }

    private ShelfEntity findShelfByIdOrThrow(Long shelfId) {
        return shelfRepository.findById(shelfId)
                .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
    }

    public Optional<ShelfEntity> getShelf(Long id, String name) {
        return shelfRepository.findByUserIdAndName(id, name);
    }
}
