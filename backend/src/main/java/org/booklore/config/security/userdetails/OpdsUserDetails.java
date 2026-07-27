package org.booklore.config.security.userdetails;

import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.OpdsUserV2;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class OpdsUserDetails implements UserDetails {
    private final BookLoreUser user;

    private final OpdsUserV2 opdsUserV2;

    @Override
    public String getUsername() {
        return opdsUserV2.getUsername();
    }

    @Override
    public String getPassword() {
        return opdsUserV2.getPasswordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isEnabled() {
        BookLoreUser.UserPermissions permissions = user.getPermissions();
        return permissions != null && (permissions.isAdmin() || permissions.isCanAccessOpds());
    }
}