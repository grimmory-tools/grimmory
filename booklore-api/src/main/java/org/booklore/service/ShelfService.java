package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.ShelfMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.request.ShelfCreateRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.ShelfType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Service
public class ShelfService {

    private final ShelfRepository shelfRepository;
    private final BookRepository bookRepository;
    private final ShelfMapper shelfMapper;
    private final BookMapper bookMapper;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final AuditService auditService;

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

    public Shelf updateShelf(Long id, ShelfCreateRequest request) {
        ShelfEntity shelfEntity = findShelfByIdOrThrow(id);
        if (request.isPublicShelf() && !authenticationService.getAuthenticatedUser().getPermissions().isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Only admins can create public shelves");
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
