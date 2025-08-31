package com.adityachandel.booklore.controller;


import com.adityachandel.booklore.model.dto.OpdsUserV2;
import com.adityachandel.booklore.model.dto.request.OpdsUserV2CreateRequest;
import com.adityachandel.booklore.service.OpdsUserV2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/opds-users")
@RequiredArgsConstructor
public class OpdsUserV2Controller {

    private final OpdsUserV2Service service;

    @GetMapping
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canAccessOpds()")
    public List<OpdsUserV2> getUsers() {
        return service.getOpdsUsers();
    }

    @PostMapping
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canAccessOpds()")
    public OpdsUserV2 createUser(@RequestBody OpdsUserV2CreateRequest createRequest) {
        return service.createOpdsUser(createRequest);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canAccessOpds()")
    public void deleteUser(@PathVariable Long id) {
        service.deleteOpdsUser(id);
    }
}
