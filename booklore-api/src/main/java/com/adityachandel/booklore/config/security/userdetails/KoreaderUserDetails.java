package com.adityachandel.booklore.config.security.userdetails;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class KoreaderUserDetails implements UserDetails {

    private final String username;
    private final String password;
    @Getter
    private final boolean syncEnabled;
    @Getter
    private final Long bookLoreUserId;
    private final Collection<? extends GrantedAuthority> authorities;

    public KoreaderUserDetails(String username, String password, boolean syncEnabled, Long bookLoreUserId, Collection<? extends GrantedAuthority> authorities) {
        this.username = username;
        this.password = password;
        this.syncEnabled = syncEnabled;
        this.bookLoreUserId = bookLoreUserId;
        this.authorities = authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
