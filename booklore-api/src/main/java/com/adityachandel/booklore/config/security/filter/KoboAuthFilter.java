package com.adityachandel.booklore.config.security.filter;

import com.adityachandel.booklore.config.security.userdetails.UserAuthenticationDetails;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.entity.UserPermissionsEntity;
import com.adityachandel.booklore.repository.KoboUserSettingsRepository;
import com.adityachandel.booklore.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class KoboAuthFilter extends OncePerRequestFilter {

    private final KoboUserSettingsRepository koboUserSettingsRepository;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith("/api/kobo/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String[] parts = path.split("/");
        if (parts.length < 4) {
            log.warn("KOBO token missing in path");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "KOBO token missing");
            return;
        }

        String token = parts[3];

        var userTokenOpt = koboUserSettingsRepository.findByToken(token);
        if (userTokenOpt.isEmpty()) {
            log.warn("Invalid KOBO token: {}", token);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid KOBO token");
            return;
        }

        var userToken = userTokenOpt.get();
        var userOpt = userRepository.findById(userToken.getUserId());

        if (userOpt.isEmpty()) {
            log.warn("User not found for token: {}", token);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
            return;
        }

        var entity = userOpt.get();
        if (entity.getPermissions() == null || !entity.getPermissions().isPermissionSyncKobo()) {
            log.warn("User {} does not have syncKobo permission", entity.getId());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient permissions");
            return;
        }

        BookLoreUser user = bookLoreUserTransformer.toDTO(entity);
        List<GrantedAuthority> authorities = getAuthorities(entity.getPermissions());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new UserAuthenticationDetails(request, user.getId()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> getAuthorities(UserPermissionsEntity permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (permissions != null) {
            addAuthorityIfPermissionGranted(authorities, "ROLE_UPLOAD", permissions.isPermissionUpload());
            addAuthorityIfPermissionGranted(authorities, "ROLE_DOWNLOAD", permissions.isPermissionDownload());
            addAuthorityIfPermissionGranted(authorities, "ROLE_EDIT_METADATA", permissions.isPermissionEditMetadata());
            addAuthorityIfPermissionGranted(authorities, "ROLE_MANIPULATE_LIBRARY", permissions.isPermissionManipulateLibrary());
            addAuthorityIfPermissionGranted(authorities, "ROLE_ADMIN", permissions.isPermissionAdmin());
            addAuthorityIfPermissionGranted(authorities, "ROLE_SYNC_KOBO", permissions.isPermissionSyncKobo());
        }
        return authorities;
    }

    private void addAuthorityIfPermissionGranted(List<GrantedAuthority> authorities, String role, boolean permissionGranted) {
        if (permissionGranted) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
    }
}