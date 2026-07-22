package com.iuims.registrar.domain.forms;

/**
 * Signals that an official Registration Form mutation cannot be committed
 * without its immutable historical version.
 */
public class RegistrationFormSnapshotException extends RuntimeException {

    public RegistrationFormSnapshotException(String message) {
        super(message);
    }

    public RegistrationFormSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
