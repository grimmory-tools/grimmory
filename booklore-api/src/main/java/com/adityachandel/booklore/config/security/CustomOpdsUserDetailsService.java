package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.entity.OpdsUserEntity;
import com.adityachandel.booklore.repository.OpdsUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOpdsUserDetailsService implements UserDetailsService {

    private final OpdsUserRepository opdsUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        OpdsUserEntity user = opdsUserRepository.findByUsername(username)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(username));

        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles("USER")
                .build();
    }
}
