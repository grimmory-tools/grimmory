package org.grimmory.service;

import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.model.dto.MagicShelf;
import org.grimmory.model.entity.MagicShelfEntity;
import org.grimmory.repository.MagicShelfRepository;
import lombok.AllArgsConstructor;
import org.grimmory.model.enums.AuditAction;
import org.grimmory.service.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Transactional(readOnly = true)
public class MagicShelfService {

    private final MagicShelfRepository magicShelfRepository;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    public List<MagicShelf> getUserShelves() {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return getShelvesForUser(userId);
    }

    public List<MagicShelf> getUserShelvesForOpds(Long userId) {
        return getShelvesForUser(userId);
    }

    private List<MagicShelf> getShelvesForUser(Long userId) {
        List<MagicShelf> shelves = magicShelfRepository.findAllByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        List<Long> userShelfIds = shelves.stream().map(MagicShelf::getId).toList();

        List<MagicShelf> publicShelves = magicShelfRepository.findAllByIsPublicIsTrue().stream()
                .map(this::toDto)
                .filter(shelf -> !userShelfIds.contains(shelf.getId()))
                .toList();

        shelves.addAll(publicShelves);
        return shelves;
    }

    @Transactional
    public MagicShelf createOrUpdateShelf(MagicShelf dto) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        if (dto.getId() != null) {
            MagicShelfEntity existing = magicShelfRepository.findById(dto.getId()).orElseThrow(() -> new IllegalArgumentException("Shelf not found"));
            if (!existing.getUserId().equals(userId)) {
                throw new SecurityException("You are not authorized to update this shelf");
            }
            if (existing.isPublic() && !authenticationService.getAuthenticatedUser().getPermissions().isAdmin()) {
                throw new SecurityException("You are not authorized to update a public shelf");
            }
            existing.setName(dto.getName());
            existing.setIcon(dto.getIcon());
            existing.setIconType(dto.getIconType());
            existing.setFilterJson(dto.getFilterJson());
            existing.setPublic(dto.getIsPublic());
            MagicShelf result = toDto(magicShelfRepository.save(existing));
            auditService.log(AuditAction.MAGIC_SHELF_UPDATED, "MagicShelf", dto.getId(), "Updated magic shelf: " + dto.getName());
            return result;
        }
        if (magicShelfRepository.existsByUserIdAndName(userId, dto.getName())) {
            throw new IllegalArgumentException("A shelf with the same name already exists for this user.");
        }
        MagicShelf result = toDto(magicShelfRepository.save(toEntity(dto, userId)));
        auditService.log(AuditAction.MAGIC_SHELF_CREATED, "MagicShelf", result.getId(), "Created magic shelf: " + dto.getName());
        return result;
    }

    @Transactional
    public void deleteShelf(Long id) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        MagicShelfEntity shelf = magicShelfRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Shelf not found"));
        if (!shelf.getUserId().equals(userId)) {
            throw new SecurityException("You are not authorized to delete this shelf");
        }
        String shelfName = shelf.getName();
        magicShelfRepository.deleteById(id);
        auditService.log(AuditAction.MAGIC_SHELF_DELETED, "MagicShelf", id, "Deleted magic shelf: " + shelfName);
    }

    private MagicShelf toDto(MagicShelfEntity entity) {
        MagicShelf dto = new MagicShelf();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setIcon(entity.getIcon());
        dto.setIconType(entity.getIconType());
        dto.setFilterJson(entity.getFilterJson());
        dto.setIsPublic(entity.isPublic());
        return dto;
    }

    private MagicShelfEntity toEntity(MagicShelf dto, Long userId) {
        MagicShelfEntity entity = new MagicShelfEntity();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setIcon(dto.getIcon());
        entity.setIconType(dto.getIconType());
        entity.setFilterJson(dto.getFilterJson());
        entity.setPublic(dto.getIsPublic());
        entity.setUserId(userId);
        return entity;
    }

    public MagicShelf getShelf(Long id) {
        MagicShelfEntity shelf = magicShelfRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Shelf not found"));
        return toDto(shelf);
    }
}
