package org.booklore.grimmlink.security;

import jakarta.servlet.FilterChain;
import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoreaderUserEntity;
import org.booklore.repository.KoreaderUserRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrimmlinkAuthFilterTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    private final KoreaderUserRepository koreaderUserRepository = mock(KoreaderUserRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final GrimmlinkAuthFilter filter = new GrimmlinkAuthFilter(
            koreaderUserRepository,
            userRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingHeadersConsistently() throws Exception {
        MockHttpServletResponse response = invoke(new MockHttpServletRequest(), mock(FilterChain.class));

        assertEquals(401, response.getStatus());
        assertEquals(
                "{\"status\":\"error\",\"message\":\"Missing GrimmLink authentication headers\"}",
                response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsDisabledSync() throws Exception {
        KoreaderUserEntity user = koreaderUser(false, true);
        when(koreaderUserRepository.findByUsername("reader")).thenReturn(Optional.of(user));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = invoke(authenticatedRequest(), chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUnlinkedUser() throws Exception {
        KoreaderUserEntity user = koreaderUser(true, false);
        when(koreaderUserRepository.findByUsername("reader")).thenReturn(Optional.of(user));

        MockHttpServletResponse response = invoke(authenticatedRequest(), mock(FilterChain.class));

        assertEquals(401, response.getStatus());
        assertEquals(
                "{\"status\":\"error\",\"message\":\"GrimmLink user is not linked to a Grimmory user\"}",
                response.getContentAsString());
    }

    @Test
    void authenticatesLinkedSyncUser() throws Exception {
        KoreaderUserEntity user = koreaderUser(true, true);
        when(koreaderUserRepository.findByUsername("reader")).thenReturn(Optional.of(user));
        when(userRepository.existsById(7L)).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = invoke(authenticatedRequest(), chain);

        assertEquals(200, response.getStatus());
        assertInstanceOf(
                KoreaderUserDetails.class,
                SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-auth-user", "reader");
        request.addHeader("x-auth-key", KEY);
        return request;
    }

    private MockHttpServletResponse invoke(
            MockHttpServletRequest request,
            FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private KoreaderUserEntity koreaderUser(boolean syncEnabled, boolean linked) {
        KoreaderUserEntity user = new KoreaderUserEntity();
        user.setUsername("reader");
        user.setPasswordMD5(KEY);
        user.setSyncEnabled(syncEnabled);
        if (linked) {
            BookLoreUserEntity reader = new BookLoreUserEntity();
            reader.setId(7L);
            user.setBookLoreUser(reader);
        }
        return user;
    }
}
