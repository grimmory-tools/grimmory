package com.adityachandel.booklore.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SecurityContextVirtualThreadTest {

    private Authentication originalAuth;
    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        originalAuth = new UsernamePasswordAuthenticationToken("testUser", "password", null);
        originalContext = new SecurityContextImpl(originalAuth);
        SecurityContextHolder.setContext(originalContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testRunWithSecurityContext_executesRunnable() throws ExecutionException, InterruptedException {
        AtomicReference<String> result = new AtomicReference<>();

        CompletableFuture<Void> future = new CompletableFuture<>();
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                result.set("executed");
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        // Wait for the virtual thread to complete
        future.get();
        assertEquals("executed", result.get());
    }

    @Test
    void testRunWithSecurityContext_preservesSecurityContext() throws ExecutionException, InterruptedException {
        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();

        CompletableFuture<Void> future = new CompletableFuture<>();
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        future.get();
        assertNotNull(capturedAuth.get());
        assertEquals(originalAuth.getName(), capturedAuth.get().getName());
    }

    @Test
    void testRunWithSecurityContext_maintainsMainThreadContext() throws ExecutionException, InterruptedException {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                // Context should be set here
                assertNotNull(SecurityContextHolder.getContext().getAuthentication());
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        future.get();

        // After the virtual thread completes, the main thread's context should still be intact
        Authentication mainThreadAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(mainThreadAuth);
        assertEquals(originalAuth.getName(), mainThreadAuth.getName());
    }

    @Test
    void testRunWithSecurityContext_withProvidedContext() throws ExecutionException, InterruptedException {
        Authentication differentAuth = new UsernamePasswordAuthenticationToken("differentUser", "password", null);
        SecurityContext differentContext = new SecurityContextImpl(differentAuth);

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();

        CompletableFuture<Void> future = new CompletableFuture<>();
        SecurityContextVirtualThread.runWithSecurityContext(differentContext, () -> {
            try {
                capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        future.get();
        assertNotNull(capturedAuth.get());
        assertEquals("differentUser", capturedAuth.get().getName());
    }

    @Test
    void testRunWithSecurityContext_withProvidedContext_clearsAfterExecution() throws ExecutionException, InterruptedException {
        Authentication differentAuth = new UsernamePasswordAuthenticationToken("differentUser", "password", null);
        SecurityContext differentContext = new SecurityContextImpl(differentAuth);

        CompletableFuture<Void> future = new CompletableFuture<>();
        SecurityContextVirtualThread.runWithSecurityContext(differentContext, () -> {
            try {
                // Different context should be set here
                assertEquals("differentUser", SecurityContextHolder.getContext().getAuthentication().getName());
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        future.get();

        // Main thread should still have original context
        Authentication mainThreadAuth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("testUser", mainThreadAuth.getName());
    }

    @Test
    void testRunWithSecurityContext_handlesException() throws ExecutionException, InterruptedException {
        CompletableFuture<Exception> future = new CompletableFuture<>();

        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                throw new RuntimeException("Test exception");
            } catch (Exception e) {
                future.complete(e);
                throw e;
            }
        });

        Exception capturedException = future.get();
        assertNotNull(capturedException);
        assertEquals("Test exception", capturedException.getMessage());
    }
}
