package com.adityachandel.booklore.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityContextVirtualThread {

    public static void runWithSecurityContext(Runnable task) {
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();

        Thread.startVirtualThread(() -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(currentAuth);
            SecurityContextHolder.setContext(context);
            try {
                task.run();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }
}
