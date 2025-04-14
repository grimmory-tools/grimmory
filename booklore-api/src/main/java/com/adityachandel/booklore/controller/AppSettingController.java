package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.SettingRequest;
import com.adityachandel.booklore.service.AppSettingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/settings")
public class AppSettingController {

    private final AppSettingService appSettingService;

    @GetMapping
    public AppSettings getAppSettings() {
        return appSettingService.getAppSettings();
    }

    @PutMapping
    public void updateSettings(@RequestBody List<SettingRequest> settingRequests) throws JsonProcessingException {
        for (SettingRequest settingRequest : settingRequests) {
            appSettingService.updateSetting(settingRequest.getName(), settingRequest.getValue());
        }
    }
}