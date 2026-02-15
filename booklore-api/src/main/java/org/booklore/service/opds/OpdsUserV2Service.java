package org.booklore.service.opds;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.OpdsUserV2Mapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.OpdsUserV2;
import org.booklore.model.dto.request.OpdsUserV2CreateRequest;
import org.booklore.model.dto.request.OpdsUserV2UpdateRequest;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.OpdsUserV2Entity;
import org.booklore.model.enums.OpdsSortOrder;
import org.booklore.repository.OpdsUserV2Repository;
import org.booklore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
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
    private final AuditService auditService;


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
                    .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : OpdsSortOrder.RECENT)
                    .build();

            OpdsUserV2 result = mapper.toDto(opdsUserV2Repository.save(opdsUserV2));
            auditService.log(AuditAction.OPDS_USER_CREATED, "OpdsUser", result.getId(), "Created OPDS user: " + request.getUsername());
            return result;
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
        String username = user.getUsername();
        opdsUserV2Repository.delete(user);
        auditService.log(AuditAction.OPDS_USER_DELETED, "OpdsUser", userId, "Deleted OPDS user: " + username);
    }

    public OpdsUserV2 updateOpdsUser(Long userId, OpdsUserV2UpdateRequest request) {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
        OpdsUserV2Entity user = opdsUserV2Repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        
        if (!user.getUser().getId().equals(bookLoreUser.getId())) {
            throw new AccessDeniedException("You are not allowed to update this user");
        }
        
        user.setSortOrder(request.sortOrder());
        OpdsUserV2 result = mapper.toDto(opdsUserV2Repository.save(user));
        auditService.log(AuditAction.OPDS_USER_UPDATED, "OpdsUser", userId, "Updated OPDS user: " + user.getUsername());
        return result;
    }

    public OpdsUserV2Entity findByUsername(String username) {
        return opdsUserV2Repository.findByUsername(username).orElse(null);
    }
}