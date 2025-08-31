package com.adityachandel.booklore.config.security.userdetails;

import com.adityachandel.booklore.model.dto.OpdsUser;
import com.adityachandel.booklore.model.dto.OpdsUserV2;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class OpdsUserDetails implements UserDetails {

    private final OpdsUserV2 opdsUserV2;
    private final OpdsUser opdsUser;

    @Override
    public String getUsername() {
        if (opdsUserV2 != null) return opdsUserV2.getUsername();
        if (opdsUser != null) return opdsUser.getUsername();
        throw new IllegalStateException("No username available");
    }

    @Override
    public String getPassword() {
        if (opdsUserV2 != null) return opdsUserV2.getPasswordHash();
        if (opdsUser != null) return opdsUser.getPassword();
        throw new IllegalStateException("No password available");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }
}