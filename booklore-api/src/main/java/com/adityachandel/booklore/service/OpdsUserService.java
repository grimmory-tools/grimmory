package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.OpdsUserMapper;
import com.adityachandel.booklore.model.dto.OpdsUser;
import com.adityachandel.booklore.model.entity.OpdsUserEntity;
import com.adityachandel.booklore.repository.OpdsUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpdsUserService {

    private final OpdsUserRepository opdsUserRepository;
    private final OpdsUserMapper opdsUserMapper;
    private final PasswordEncoder passwordEncoder;

    public void createOpdsUser(String username, String password) {
        if (opdsUserRepository.existsByUsername(username)) {
            throw ApiError.USERNAME_ALREADY_TAKEN.createException(username);
        }
        OpdsUserEntity opdsUser = new OpdsUserEntity();
        opdsUser.setUsername(username);
        opdsUser.setPassword(passwordEncoder.encode(password));
        opdsUserRepository.save(opdsUser);
    }

    public List<OpdsUser> getOpdsUsers() {
        return opdsUserRepository.findAll()
                .stream()
                .map(opdsUserMapper::toOpdsUser)
                .collect(Collectors.toList());
    }

    public void deleteOpdsUser(Long id) {
        if (!opdsUserRepository.existsById(id)) {
            throw ApiError.USER_NOT_FOUND.createException(id);
        }
        opdsUserRepository.deleteById(id);
    }

    public void resetPassword(Long id, String newPassword) {
        OpdsUserEntity opdsUser = opdsUserRepository.findById(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        opdsUser.setPassword(passwordEncoder.encode(newPassword));
        opdsUserRepository.save(opdsUser);
    }
}
