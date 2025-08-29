package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.MagicShelf;
import com.adityachandel.booklore.model.entity.MagicShelfEntity;
import com.adityachandel.booklore.repository.MagicShelfRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@AllArgsConstructor
@Service
public class MagicShelfService {

    private final MagicShelfRepository repository;
    private final AuthenticationService authenticationService;

    public List<MagicShelf> getUserShelves() {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        return repository.findAllByUserId(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public MagicShelf createOrUpdateShelf(MagicShelf dto) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        if (dto.getId() != null) {
            MagicShelfEntity existing = repository.findById(dto.getId()).orElseThrow(() -> new IllegalArgumentException("Shelf not found"));
            if (!existing.getUserId().equals(userId)) {
                throw new SecurityException("You are not authorized to update this shelf");
            }
            existing.setName(dto.getName());
            existing.setIcon(dto.getIcon());
            existing.setFilterJson(dto.getFilterJson());
            return toDto(repository.save(existing));
        }
        if (repository.existsByUserIdAndName(userId, dto.getName())) {
            throw new IllegalArgumentException("A shelf with the same name already exists for this user.");
        }
        return toDto(repository.save(toEntity(dto, userId)));
    }

    @Transactional
    public void deleteShelf(Long id) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        MagicShelfEntity shelf = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Shelf not found"));
        if (!shelf.getUserId().equals(userId)) {
            throw new SecurityException("You are not authorized to delete this shelf");
        }
        repository.deleteById(id);
    }

    private MagicShelf toDto(MagicShelfEntity entity) {
        MagicShelf dto = new MagicShelf();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setIcon(entity.getIcon());
        dto.setFilterJson(entity.getFilterJson());
        return dto;
    }

    private MagicShelfEntity toEntity(MagicShelf dto, Long userId) {
        MagicShelfEntity entity = new MagicShelfEntity();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setIcon(dto.getIcon());
        entity.setFilterJson(dto.getFilterJson());
        entity.setUserId(userId);
        return entity;
    }

    public MagicShelf getShelf(Long id) {
        MagicShelfEntity shelf = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Shelf not found"));
        return toDto(shelf);
    }
}