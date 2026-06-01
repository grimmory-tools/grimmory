package org.booklore.config.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.userdetails.UserAuthenticationDetails;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.repository.KoboUserSettingsRepository;
import org.booklore.service.user.UserCacheService;
import org.springframework.boot.web.servlet.FilterRegistration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
@FilterRegistration(enabled = false)
public class KoboAuthFilter extends OncePerRequestFilter {

    private final KoboUserSettingsRepository koboUserSettingsRepository;
    private final UserCacheService userCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String[] parts = path.split("/");
        if (parts.length < 4) {
            log.warn("KOBO token missing in path");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "KOBO token missing");
            return;
        }

        String token = parts[3];

        var userTokenOpt = koboUserSettingsRepository.findByToken(token);
        if (userTokenOpt.isEmpty()) {
            log.warn("Invalid KOBO token");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid KOBO token");
            return;
        }

        var userToken = userTokenOpt.get();
        BookLoreUser user = userCacheService.getUserDetails(userToken.getUserId());

        if (user == null) {
            log.warn("User not found for KOBO token");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
            return;
        }

        if (user.getPermissions() == null || !user.getPermissions().isCanSyncKobo()) {
            log.warn("User {} does not have syncKobo permission", user.getId());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient permissions");
            return;
        }

        List<GrantedAuthority> authorities = getAuthorities(user.getPermissions());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> getAuthorities(BookLoreUser.UserPermissions permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (permissions != null) {
            addAuthorityIfPermissionGranted(authorities, "ROLE_UPLOAD", permissions.isCanUpload());
            addAuthorityIfPermissionGranted(authorities, "ROLE_DOWNLOAD", permissions.isCanDownload());
            addAuthorityIfPermissionGranted(authorities, "ROLE_EDIT_METADATA", permissions.isCanEditMetadata());
            addAuthorityIfPermissionGranted(authorities, "ROLE_MANAGE_LIBRARY", permissions.isCanManageLibrary());
            addAuthorityIfPermissionGranted(authorities, "ROLE_ADMIN", permissions.isAdmin());
            addAuthorityIfPermissionGranted(authorities, "ROLE_SYNC_KOBO", permissions.isCanSyncKobo());
        }
        return authorities;
    }

    private void addAuthorityIfPermissionGranted(List<GrantedAuthority> authorities, String role, boolean permissionGranted) {
        if (permissionGranted) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
    }
}