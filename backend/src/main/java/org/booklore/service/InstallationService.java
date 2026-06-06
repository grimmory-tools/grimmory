package org.booklore.service;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Installation;
import org.booklore.model.entity.AppSettingEntity;
import org.booklore.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class InstallationService {

    private static final String INSTALLATION_ID_KEY = "installation_id";

    private final AppSettingsRepository appSettingsRepository;
    private final ObjectMapper objectMapper;

    public InstallationService(AppSettingsRepository appSettingsRepository, ObjectMapper objectMapper) {
        this.appSettingsRepository = appSettingsRepository;
        this.objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
    }

    public Installation getOrCreateInstallation() {
        AppSettingEntity setting = appSettingsRepository.findByName(INSTALLATION_ID_KEY);

        if (setting == null) {
            return createNewInstallation();
        }

        try {
            return objectMapper.readValue(setting.getVal(), Installation.class);
        } catch (Exception e) {
            log.warn("Failed to parse installation ID, creating new one", e);
            return createNewInstallation();
        }
    }

    private Installation createNewInstallation() {
        Instant now = Instant.now();
        String uuid = UUID.randomUUID().toString();

        String combined = now.toString() + "_" + uuid;
        String installationId = hashToSha256(combined).substring(0, 24);

        Installation installation = new Installation(installationId, now);
        saveInstallation(installation);

        log.info("Generated new installation ID");
        return installation;
    }

    private void saveInstallation(Installation installation) {
        try {
            String json = objectMapper.writeValueAsString(installation);
            AppSettingEntity setting = appSettingsRepository.findByName(INSTALLATION_ID_KEY);

            if (setting == null) {
                setting = new AppSettingEntity();
                setting.setName(INSTALLATION_ID_KEY);
            }

            setting.setVal(json);
            appSettingsRepository.save(setting);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save installation ID", e);
        }
    }

    private String hashToSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
