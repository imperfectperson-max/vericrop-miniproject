package org.vericrop.gui.exception;

/**
 * Exception thrown when user creation fails in the database.
 * Provides specific information about which field (if any) caused the conflict.
 */
public class UserCreationException extends Exception {
    
    /**
     * Enum representing the type of constraint violation that caused the failure.
     */
    public enum ConflictType {
        /** Username already exists in the database */
        DUPLICATE_USERNAME,
        /** Email already exists in the database */
        DUPLICATE_EMAIL,
        /** Unknown database error occurred */
        DATABASE_ERROR
    }
    
    private final ConflictType conflictType;
    private final String fieldName;
    
    /**
     * Create a new UserCreationException.
     * 
     * @param message Human-readable error message
     * @param conflictType The type of conflict that caused the failure
     * @param fieldName The name of the field that caused the conflict (e.g., "username", "email")
     */
    public UserCreationException(String message, ConflictType conflictType, String fieldName) {
        super(message);
        this.conflictType = conflictType;
        this.fieldName = fieldName;
    }
    
    /**
     * Create a new UserCreationException with a cause.
     * 
     * @param message Human-readable error message
     * @param conflictType The type of conflict that caused the failure
     * @param fieldName The name of the field that caused the conflict
     * @param cause The underlying exception that caused this exception
     */
    public UserCreationException(String message, ConflictType conflictType, String fieldName, Throwable cause) {
        super(message, cause);
        this.conflictType = conflictType;
        this.fieldName = fieldName;
    }
    
    /**
     * Get the type of conflict that caused the user creation to fail.
     * 
     * @return The conflict type
     */
    public ConflictType getConflictType() {
        return conflictType;
    }
    
    /**
     * Get the name of the field that caused the conflict.
     * 
     * @return The field name (e.g., "username", "email"), or null for general errors
     */
    public String getFieldName() {
        return fieldName;
    }
    
    /**
     * Check if this exception is due to a duplicate username.
     * 
     * @return true if the conflict is a duplicate username
     */
    public boolean isDuplicateUsername() {
        return conflictType == ConflictType.DUPLICATE_USERNAME;
    }
    
    /**
     * Check if this exception is due to a duplicate email.
     * 
     * @return true if the conflict is a duplicate email
     */
    public boolean isDuplicateEmail() {
        return conflictType == ConflictType.DUPLICATE_EMAIL;
    }
    
    /**
     * Check if this exception is due to a general database error.
     * 
     * @return true if this is a general database error
     */
    public boolean isDatabaseError() {
        return conflictType == ConflictType.DATABASE_ERROR;
    }
}
