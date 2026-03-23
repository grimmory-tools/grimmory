package org.booklore.config.security.aspect;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.booklore.config.security.annotation.CheckBookAccess;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.restriction.ContentRestrictionService;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Aspect
@Component
@RequiredArgsConstructor
public class BookAccessAspect {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");
    private final AuthenticationService authenticationService;
    private final BookRepository bookRepository;
    private final ContentRestrictionService contentRestrictionService;

    @Before("@annotation(org.booklore.config.security.annotation.CheckBookAccess)")
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

        BookEntity bookEntity = bookRepository.findByIdWithMetadata(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        BookLoreUser user = authenticationService.getAuthenticatedUser();

        if (user.getPermissions().isAdmin()) {
            return;
        }

        boolean hasLibraryAccess = user.getAssignedLibraries().stream().anyMatch(library -> library.getId().equals(bookEntity.getLibrary().getId()));

        if (!hasLibraryAccess) {
            throw ApiError.FORBIDDEN.createException("You are not authorized to access this book.");
        }

        List<BookEntity> filteredBooks = contentRestrictionService.applyRestrictions(List.of(bookEntity), user.getId());
        if (filteredBooks.isEmpty()) {
            throw ApiError.FORBIDDEN.createException("You are not authorized to access this book.");
        }
    }

    private Long extractBookId(Object[] args, String[] paramNames, String targetParamName) {
        for (int i = 0; i < paramNames.length; i++) {
            if (Objects.equals(paramNames[i], targetParamName)) {
                Object arg = args[i];
                if (arg instanceof Long) {
                    return (Long) arg;
                } else if (arg instanceof String str && NUMERIC_PATTERN.matcher(str).matches()) {
                    return Long.valueOf(str);
                }
            }
        }
        return null;
    }
}