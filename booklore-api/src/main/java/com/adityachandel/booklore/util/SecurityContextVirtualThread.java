package com.adityachandel.booklore.util;

import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@UtilityClass
public class SecurityContextVirtualThread {

    public void runWithSecurityContext(Runnable task) {
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

    public void runWithSecurityContext(SecurityContext parentContext, Runnable task) {
        Thread.startVirtualThread(() -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(parentContext.getAuthentication());
            SecurityContextHolder.setContext(context);
            try {
                task.run();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }
}
