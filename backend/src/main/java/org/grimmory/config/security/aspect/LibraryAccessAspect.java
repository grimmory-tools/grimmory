package org.grimmory.config.security.aspect;

import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.config.security.annotation.CheckLibraryAccess;
import org.grimmory.exception.ApiError;
import org.grimmory.model.dto.BookLoreUser;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.regex.Pattern;

@Aspect
@Component
@RequiredArgsConstructor
public class LibraryAccessAspect {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");
    private final AuthenticationService authenticationService;

    @Before("@annotation(org.grimmory.config.security.annotation.CheckLibraryAccess)")
    public void checkLibraryAccess(JoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        CheckLibraryAccess annotation = method.getAnnotation(CheckLibraryAccess.class);

        if (annotation == null) return;

        Long libraryId = extractLibraryId(methodSignature.getParameterNames(), joinPoint.getArgs(), annotation.libraryIdParam());

        if (libraryId == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Library ID not found in method parameters.");
        }

        BookLoreUser user = authenticationService.getAuthenticatedUser();

        if (user == null) {
            throw ApiError.FORBIDDEN.createException("Authentication required.");
        }

        if (user.getPermissions().isAdmin()) return;

        boolean hasAccess = user.getAssignedLibraries().stream().anyMatch(lib -> lib.getId().equals(libraryId));

        if (!hasAccess) {
            throw ApiError.FORBIDDEN.createException("You are not authorized to access this library.");
        }
    }

    private Long extractLibraryId(String[] paramNames, Object[] args, String targetParam) {
        for (int i = 0; i < paramNames.length; i++) {
            if (Objects.equals(paramNames[i], targetParam)) {
                Object arg = args[i];
                if (arg instanceof Long) {
                    return (Long) arg;
                } else if (arg instanceof String str && NUMERIC_PATTERN.matcher(str).matches()) {
                    return Long.parseLong(str);
                }
            }
        }
        return null;
    }
}