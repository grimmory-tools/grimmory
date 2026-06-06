package org.booklore.config.security.service;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.exception.ApiError;
import org.booklore.mapper.OpdsUserV2Mapper;
import org.booklore.model.dto.OpdsUserV2;
import org.booklore.model.entity.OpdsUserV2Entity;
import org.booklore.repository.OpdsUserV2Repository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class OpdsUserDetailsService implements UserDetailsService {

    private final OpdsUserV2Repository opdsUserV2Repository;
    private final OpdsUserV2Mapper opdsUserV2Mapper;

    @Override
    @Transactional(readOnly = true)
    public OpdsUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        OpdsUserV2Entity userV2 = opdsUserV2Repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        OpdsUserV2 mappedCredential = opdsUserV2Mapper.toDto(userV2);
        return new OpdsUserDetails(mappedCredential);
    }
}