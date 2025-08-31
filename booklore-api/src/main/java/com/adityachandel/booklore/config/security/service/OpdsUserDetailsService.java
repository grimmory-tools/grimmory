package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.OpdsUserMapper;
import com.adityachandel.booklore.mapper.OpdsUserV2Mapper;
import com.adityachandel.booklore.repository.OpdsUserV2Repository;
import com.adityachandel.booklore.repository.OpdsUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class OpdsUserDetailsService implements UserDetailsService {

    private final OpdsUserRepository opdsUserRepository;
    private final OpdsUserV2Repository opdsUserV2Repository;
    private final OpdsUserMapper opdsUserMapper;
    private final OpdsUserV2Mapper opdsUserV2Mapper;

    @Override
    public OpdsUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return opdsUserRepository.findByUsername(username)
                .map(user -> {
                    var mappedUser = opdsUserMapper.toOpdsUser(user);
                    return new OpdsUserDetails(null, mappedUser);
                })
                .orElseGet(() -> {
                    var userV2 = opdsUserV2Repository.findByUsername(username)
                            .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(username));
                    var mappedCredential = opdsUserV2Mapper.toDto(userV2);
                    return new OpdsUserDetails(mappedCredential, null);
                });
    }
}