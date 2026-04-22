package org.booklore.config.security.annotation;


import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckLibraryAccess {
    String libraryIdParam();
}
