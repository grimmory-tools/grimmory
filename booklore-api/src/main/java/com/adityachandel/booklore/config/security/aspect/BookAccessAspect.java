package com.adityachandel.booklore.config.security.aspect;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.config.security.annotation.CheckBookAccess;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

@Aspect
@Component
@RequiredArgsConstructor
public class BookAccessAspect {

    private final AuthenticationService authenticationService;
    private final BookRepository bookRepository;

    @Before("@annotation(com.adityachandel.booklore.config.security.annotation.CheckBookAccess)")
    public void checkBookAccess(JoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        CheckBookAccess annotation = method.getAnnotation(CheckBookAccess.class);

        if (annotation == null) {
            return;
        }

        Long bookId = extractBookId(joinPoint.getArgs(), methodSignature.getParameterNames(), annotation.bookIdParam());
        if (bookId == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Missing or invalid book ID in method parameters.");
        }

        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        BookLoreUser user = authenticationService.getAuthenticatedUser();

        if (user.getPermissions().isAdmin()) {
            return;
        }

        boolean hasAccess = user.getAssignedLibraries().stream().anyMatch(library -> library.getId().equals(bookEntity.getLibrary().getId()));

        if (!hasAccess) {
            throw ApiError.FORBIDDEN.createException("You are not authorized to access this book.");
        }
    }

    private Long extractBookId(Object[] args, String[] paramNames, String targetParamName) {
        for (int i = 0; i < paramNames.length; i++) {
            if (Objects.equals(paramNames[i], targetParamName)) {
                Object arg = args[i];
                if (arg instanceof Long) {
                    return (Long) arg;
                } else if (arg instanceof String str && str.matches("\\d+")) {
                    return Long.valueOf(str);
                }
            }
        }
        return null;
    }
}