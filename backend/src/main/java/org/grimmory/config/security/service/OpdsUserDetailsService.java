package org.grimmory.config.security.service;

import lombok.RequiredArgsConstructor;
import org.grimmory.config.security.userdetails.OpdsUserDetails;
import org.grimmory.exception.ApiError;
import org.grimmory.mapper.OpdsUserV2Mapper;
import org.grimmory.model.dto.OpdsUserV2;
import org.grimmory.model.entity.OpdsUserV2Entity;
import org.grimmory.repository.OpdsUserV2Repository;
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