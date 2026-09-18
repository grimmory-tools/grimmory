package org.booklore.service.migration.migrations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.settings.AppSettingKey;
import org.booklore.model.entity.AppSettingEntity;
import org.booklore.repository.AppSettingsRepository;
import org.booklore.service.migration.Migration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@RequiredArgsConstructor
public class OIDCClientSecretSeparateKey implements Migration {
    private static final String KEY_CLIENT_SECRET = "clientSecret";
    private final AppSettingsRepository appSettingsRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getKey() {
        return "oidcClientSecretSeparateKey";
    }

    @Override
    public String getDescription() {
        return "Migrate existing OIDC Provider Client Secret from Provider Details to a dedicated key";
    }

    @Override
    public void execute() {
        log.info("Executing migration: {}", getKey());

        AppSettingEntity providerDetailsSetting = appSettingsRepository.findByName(AppSettingKey.OIDC_PROVIDER_DETAILS.getDbKey());
        AppSettingEntity providerClientSecretSetting = appSettingsRepository.findByName(AppSettingKey.OIDC_PROVIDER_CLIENT_SECRET.getDbKey());

        if (providerDetailsSetting == null) {
            log.debug("Setting does not exist, skipping");
            log.info("Completed migration: {}", getKey());
            return;
        }

        if (providerClientSecretSetting == null) {
            providerClientSecretSetting = new AppSettingEntity();
            providerClientSecretSetting.setName(AppSettingKey.OIDC_PROVIDER_CLIENT_SECRET.getDbKey());
        }

        try {
            var node = objectMapper.readTree(providerDetailsSetting.getVal());

            if (node instanceof ObjectNode objectNode) {
                if (providerClientSecretSetting.getVal() != null) {
                    log.info("Skipping persisting client secret to new field: already exists");
                } else if (objectNode.has(KEY_CLIENT_SECRET) && objectNode.get(KEY_CLIENT_SECRET).isString()) {
                    log.debug("Migrated client secret to separate key");
                    providerClientSecretSetting.setVal(objectNode.get(KEY_CLIENT_SECRET).asString());
                }

                log.debug("Removing client secret from public settings");
                objectNode.remove(KEY_CLIENT_SECRET);

                providerDetailsSetting.setVal(objectMapper.writeValueAsString(objectNode));
            } else {
                log.info("Unable to read OIDC Provider Details JSON, purging value");
                providerDetailsSetting.setVal(null);
            }
        } catch (Exception e) {
            log.info("Unable to read OIDC Provider Details JSON, purging value");
            providerDetailsSetting.setVal(null);
        }

        appSettingsRepository.save(providerDetailsSetting);
        appSettingsRepository.save(providerClientSecretSetting);

        log.info("Completed migration: {}", getKey());
    }
}

