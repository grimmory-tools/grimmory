package com.adityachandel.booklore.service.opds;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.OpdsUserV2Mapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.OpdsUserV2;
import com.adityachandel.booklore.model.dto.request.OpdsUserV2CreateRequest;
import com.adityachandel.booklore.model.dto.request.OpdsUserV2UpdateRequest;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.OpdsUserV2Entity;
import com.adityachandel.booklore.repository.OpdsUserV2Repository;
import com.adityachandel.booklore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OpdsUserV2Service {

    private final OpdsUserV2Repository opdsUserV2Repository;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final OpdsUserV2Mapper mapper;
    private final PasswordEncoder passwordEncoder;


    public List<OpdsUserV2> getOpdsUsers() {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
        List<OpdsUserV2Entity> users = opdsUserV2Repository.findByUserId(bookLoreUser.getId());
        return mapper.toDto(users);
    }

    public OpdsUserV2 createOpdsUser(OpdsUserV2CreateRequest request) {
        try {
            BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
            BookLoreUserEntity userEntity = userRepository.findById(bookLoreUser.getId())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + bookLoreUser.getId()));

            OpdsUserV2Entity opdsUserV2 = OpdsUserV2Entity.builder()
                    .user(userEntity)
                    .username(request.getUsername())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : com.adityachandel.booklore.model.enums.OpdsSortOrder.RECENT)
                    .build();

            return mapper.toDto(opdsUserV2Repository.save(opdsUserV2));
        } catch (DataIntegrityViolationException e) {
            if (e.getMostSpecificCause().getMessage().contains("uq_username")) {
                throw new DataIntegrityViolationException("Username '" + request.getUsername() + "' is already taken");
            }
            throw e;
        }
    }

    public void deleteOpdsUser(Long userId) {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
        OpdsUserV2Entity user = opdsUserV2Repository.findById(userId).orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        if (!user.getUser().getId().equals(bookLoreUser.getId())) {
            throw new AccessDeniedException("You are not allowed to delete this user");
        }
        opdsUserV2Repository.delete(user);
    }

    public OpdsUserV2 updateOpdsUser(Long userId, OpdsUserV2UpdateRequest request) {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
        OpdsUserV2Entity user = opdsUserV2Repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        
        if (!user.getUser().getId().equals(bookLoreUser.getId())) {
            throw new AccessDeniedException("You are not allowed to update this user");
        }
        
        user.setSortOrder(request.sortOrder());
        return mapper.toDto(opdsUserV2Repository.save(user));
    }

    public OpdsUserV2Entity findByUsername(String username) {
        return opdsUserV2Repository.findByUsername(username).orElse(null);
    }
}