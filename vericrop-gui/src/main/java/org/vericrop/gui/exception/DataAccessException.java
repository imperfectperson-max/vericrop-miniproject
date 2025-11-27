package org.vericrop.gui.exception;

/**
 * Exception thrown when a database access error occurs in DAO operations.
 * This is a runtime exception that wraps SQL exceptions with additional context.
 */
public class DataAccessException extends RuntimeException {
    
    /**
     * Create a new DataAccessException.
     * 
     * @param message Human-readable error message
     */
    public DataAccessException(String message) {
        super(message);
    }
    
    /**
     * Create a new DataAccessException with a cause.
     * 
     * @param message Human-readable error message
     * @param cause The underlying exception that caused this exception
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
