package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.mapper.OpdsUserMapper;
import com.adityachandel.booklore.mapper.OpdsUserV2Mapper;
import com.adityachandel.booklore.model.entity.OpdsUserEntity;
import com.adityachandel.booklore.model.entity.OpdsUserV2Entity;
import com.adityachandel.booklore.repository.OpdsUserRepository;
import com.adityachandel.booklore.repository.OpdsUserV2Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpdsUserDetailsServiceTest {

    private OpdsUserRepository opdsUserRepository;
    private OpdsUserV2Repository opdsUserV2Repository;
    private OpdsUserMapper opdsUserMapper;
    private OpdsUserV2Mapper opdsUserV2Mapper;
    private OpdsUserDetailsService service;

    @BeforeEach
    void setUp() {
        opdsUserRepository = mock(OpdsUserRepository.class);
        opdsUserV2Repository = mock(OpdsUserV2Repository.class);
        opdsUserMapper = mock(OpdsUserMapper.class);
        opdsUserV2Mapper = mock(OpdsUserV2Mapper.class);

        service = new OpdsUserDetailsService(opdsUserRepository, opdsUserV2Repository, opdsUserMapper, opdsUserV2Mapper);
    }

    @Test
    void loadUserByUsername_primaryRepositoryHit_usesPrimaryMapperAndDoesNotCallV2() {
        String username = "primaryUser";

        Optional<OpdsUserEntity> primaryEntity = Optional.of(mock(OpdsUserEntity.class));
        when(opdsUserRepository.findByUsername(username)).thenReturn(primaryEntity);

        OpdsUserDetails result = service.loadUserByUsername(username);

        assertNotNull(result, "Expected non-null OpdsUserDetails when primary repo returns a user");
        verify(opdsUserMapper, times(1)).toOpdsUser(any(OpdsUserEntity.class));
        verify(opdsUserV2Repository, never()).findByUsername(anyString());
    }

    @Test
    void loadUserByUsername_primaryEmpty_v2RepositoryHit_usesV2Mapper() {
        String username = "v2User";

        when(opdsUserRepository.findByUsername(username)).thenReturn(Optional.empty());

        Optional<OpdsUserV2Entity> v2Entity = Optional.of(mock(OpdsUserV2Entity.class));
        when(opdsUserV2Repository.findByUsername(username)).thenReturn(v2Entity);

        OpdsUserDetails result = service.loadUserByUsername(username);

        assertNotNull(result, "Expected non-null OpdsUserDetails when v2 repo returns a user");
        verify(opdsUserRepository, times(1)).findByUsername(username);
        verify(opdsUserV2Repository, times(1)).findByUsername(username);
    }

    @Test
    void loadUserByUsername_bothRepositoriesEmpty_throwsException() {
        String username = "missingUser";

        when(opdsUserRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(opdsUserV2Repository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> service.loadUserByUsername(username), "Expected an exception when user not found in either repository");
    }
}
