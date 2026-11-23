package org.booklore.service.library;

final class BookFileMutationGuardException extends IllegalStateException {
    BookFileMutationGuardException(RuntimeException cause) {
        super("Cannot process library group: " + cause.getMessage(), cause);
    }
}
