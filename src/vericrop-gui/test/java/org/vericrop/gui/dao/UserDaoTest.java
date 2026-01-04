package org.vericrop.gui.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vericrop.gui.exception.DataAccessException;
import org.vericrop.gui.exception.UserCreationException;
import org.vericrop.gui.exception.UserCreationException.ConflictType;
import org.vericrop.gui.models.User;

import javax.sql.DataSource;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserDao.
 * Tests verify database operations for user management including registration error handling.
 */
class UserDaoTest {
    
    private DataSource mockDataSource;
    private Connection mockConnection;
    private PreparedStatement mockStatement;
    private ResultSet mockResultSet;
    
    private UserDao userDao;
    
    @BeforeEach
    void setUp() throws SQLException {
        mockDataSource = mock(DataSource.class);
        mockConnection = mock(Connection.class);
        mockStatement = mock(PreparedStatement.class);
        mockResultSet = mock(ResultSet.class);
        
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        
        userDao = new UserDao(mockDataSource);
    }
    
    // ==================== createUser Tests ====================
    
    @Test
    void testCreateUser_Success() throws Exception {
        // Given - simulate successful INSERT with RETURNING
        Timestamp now = new Timestamp(System.currentTimeMillis());
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong("id")).thenReturn(1L);
        when(mockResultSet.getTimestamp("created_at")).thenReturn(now);
        when(mockResultSet.getTimestamp("updated_at")).thenReturn(now);
        
        // When
        User user = userDao.createUser("newuser", "SecurePass123!", "new@example.com", "New User", "PRODUCER");
        
        // Then
        assertNotNull(user);
        assertEquals("newuser", user.getUsername());
        assertEquals("new@example.com", user.getEmail());
        assertEquals("New User", user.getFullName());
        assertEquals("PRODUCER", user.getRole());
        assertEquals(1L, user.getId());
    }
    
    @Test
    void testCreateUser_DuplicateUsername_ThrowsUserCreationException() throws SQLException {
        // Given - simulate PostgreSQL unique violation on username
        SQLException sqlException = new SQLException(
            "ERROR: duplicate key value violates unique constraint \"users_username_key\"", 
            "23505"  // PostgreSQL unique violation code
        );
        when(mockStatement.executeQuery()).thenThrow(sqlException);
        
        // When/Then
        UserCreationException exception = assertThrows(
            UserCreationException.class,
            () -> userDao.createUser("existinguser", "SecurePass123!", "new@example.com", "Test User", "PRODUCER")
        );
        
        assertTrue(exception.isDuplicateUsername());
        assertFalse(exception.isDuplicateEmail());
        assertEquals(ConflictType.DUPLICATE_USERNAME, exception.getConflictType());
        assertEquals("username", exception.getFieldName());
        assertNotNull(exception.getCause());
    }
    
    @Test
    void testCreateUser_DuplicateEmail_ThrowsUserCreationException() throws SQLException {
        // Given - simulate PostgreSQL unique violation on email
        SQLException sqlException = new SQLException(
            "ERROR: duplicate key value violates unique constraint \"users_email_key\"", 
            "23505"  // PostgreSQL unique violation code
        );
        when(mockStatement.executeQuery()).thenThrow(sqlException);
        
        // When/Then
        UserCreationException exception = assertThrows(
            UserCreationException.class,
            () -> userDao.createUser("newuser", "SecurePass123!", "existing@example.com", "Test User", "PRODUCER")
        );
        
        assertTrue(exception.isDuplicateEmail());
        assertFalse(exception.isDuplicateUsername());
        assertEquals(ConflictType.DUPLICATE_EMAIL, exception.getConflictType());
        assertEquals("email", exception.getFieldName());
        assertNotNull(exception.getCause());
    }
    
    @Test
    void testCreateUser_GenericDuplicateKey_ThrowsUserCreationException() throws SQLException {
        // Given - simulate generic duplicate key error (could be username or email)
        SQLException sqlException = new SQLException(
            "ERROR: duplicate key value violates unique constraint", 
            "23505"
        );
        when(mockStatement.executeQuery()).thenThrow(sqlException);
        
        // When/Then
        UserCreationException exception = assertThrows(
            UserCreationException.class,
            () -> userDao.createUser("user", "SecurePass123!", "email@example.com", "Test", "PRODUCER")
        );
        
        assertTrue(exception.isDatabaseError());
        assertEquals(ConflictType.DATABASE_ERROR, exception.getConflictType());
        assertNull(exception.getFieldName());
    }
    
    @Test
    void testCreateUser_DatabaseError_ThrowsUserCreationException() throws SQLException {
        // Given - simulate general database error
        SQLException sqlException = new SQLException("Connection timed out", "08001");
        when(mockStatement.executeQuery()).thenThrow(sqlException);
        
        // When/Then
        UserCreationException exception = assertThrows(
            UserCreationException.class,
            () -> userDao.createUser("user", "SecurePass123!", "email@example.com", "Test", "PRODUCER")
        );
        
        assertTrue(exception.isDatabaseError());
        assertNotNull(exception.getCause());
    }
    
    // ==================== usernameExists Tests ====================
    
    @Test
    void testUsernameExists_ReturnsTrue_WhenUsernameExists() throws SQLException {
        // Given
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getInt(1)).thenReturn(1);
        
        // When
        boolean exists = userDao.usernameExists("existinguser");
        
        // Then
        assertTrue(exists);
    }
    
    @Test
    void testUsernameExists_ReturnsFalse_WhenUsernameNotExists() throws SQLException {
        // Given
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getInt(1)).thenReturn(0);
        
        // When
        boolean exists = userDao.usernameExists("newuser");
        
        // Then
        assertFalse(exists);
    }
    
    @Test
    void testUsernameExists_ReturnsFalse_WhenUsernameNull() {
        // When
        boolean exists = userDao.usernameExists(null);
        
        // Then
        assertFalse(exists);
    }
    
    @Test
    void testUsernameExists_ReturnsFalse_WhenUsernameEmpty() {
        // When
        boolean exists = userDao.usernameExists("");
        
        // Then
        assertFalse(exists);
    }
    
    @Test
    void testUsernameExists_ThrowsDataAccessException_OnDatabaseError() throws SQLException {
        // Given
        when(mockStatement.executeQuery()).thenThrow(new SQLException("Connection error"));
        
        // When/Then
        assertThrows(DataAccessException.class, () -> userDao.usernameExists("testuser"));
    }
    
    // ==================== emailExists Tests ====================
    
    @Test
    void testEmailExists_ReturnsTrue_WhenEmailExists() throws SQLException {
        // Given
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getInt(1)).thenReturn(1);
        
        // When
        boolean exists = userDao.emailExists("existing@example.com");
        
        // Then
        assertTrue(exists);
    }
    
    @Test
    void testEmailExists_ReturnsFalse_WhenEmailNotExists() throws SQLException {
        // Given
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getInt(1)).thenReturn(0);
        
        // When
        boolean exists = userDao.emailExists("new@example.com");
        
        // Then
        assertFalse(exists);
    }
    
    @Test
    void testEmailExists_ReturnsFalse_WhenEmailNull() {
        // When
        boolean exists = userDao.emailExists(null);
        
        // Then
        assertFalse(exists);
    }
    
    @Test
    void testEmailExists_ReturnsFalse_WhenEmailEmpty() {
        // When
        boolean exists = userDao.emailExists("   ");
        
        // Then
        assertFalse(exists);
    }
    
    @Test
    void testEmailExists_ThrowsDataAccessException_OnDatabaseError() throws SQLException {
        // Given
        when(mockStatement.executeQuery()).thenThrow(new SQLException("Connection error"));
        
        // When/Then
        assertThrows(DataAccessException.class, () -> userDao.emailExists("test@example.com"));
    }
    
    // ==================== Exception Message Tests ====================
    
    @Test
    void testCreateUser_DuplicateUsername_ExceptionMessage() throws SQLException {
        // Given - simulate error with username constraint name
        SQLException sqlException = new SQLException(
            "duplicate key value violates unique constraint \"users_username_key\"", 
            "23505"
        );
        when(mockStatement.executeQuery()).thenThrow(sqlException);
        
        // When/Then
        UserCreationException exception = assertThrows(
            UserCreationException.class,
            () -> userDao.createUser("duplicateuser", "Pass123!", "email@test.com", "Test", "PRODUCER")
        );
        
        // The message should mention the username
        assertTrue(exception.getMessage().contains("duplicateuser") || 
                   exception.getMessage().toLowerCase().contains("username"));
    }
    
    @Test
    void testCreateUser_DuplicateEmail_ExceptionMessage() throws SQLException {
        // Given - simulate error with email constraint name
        SQLException sqlException = new SQLException(
            "duplicate key value violates unique constraint \"users_email_key\"", 
            "23505"
        );
        when(mockStatement.executeQuery()).thenThrow(sqlException);
        
        // When/Then
        UserCreationException exception = assertThrows(
            UserCreationException.class,
            () -> userDao.createUser("newuser", "Pass123!", "dupe@example.com", "Test", "PRODUCER")
        );
        
        // The message should mention email
        assertTrue(exception.getMessage().toLowerCase().contains("email"));
    }
}
