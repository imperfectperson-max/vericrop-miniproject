package org.vericrop.gui.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService.
 * Tests database authentication and demo mode behavior.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private String originalDemoMode;

    @BeforeEach
    void setUp() throws SQLException {
        // Save original demo mode state
        originalDemoMode = System.getProperty("vericrop.demoMode");
        
        // Clear demo mode for tests
        System.clearProperty("vericrop.demoMode");
        
        // Setup mocks
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet);
    }

    @AfterEach
    void tearDown() {
        // Restore original demo mode state
        if (originalDemoMode != null) {
            System.setProperty("vericrop.demoMode", originalDemoMode);
        } else {
            System.clearProperty("vericrop.demoMode");
        }
    }

    // ==================== Demo Mode Tests ====================

    @Test
    void testDemoModeDisabledByDefault() {
        AuthenticationService service = new AuthenticationService(dataSource);
        assertFalse(service.isDemoMode(), "Demo mode should be disabled by default");
    }

    @Test
    void testDemoModeEnabledExplicitly() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService(dataSource);
        assertTrue(service.isDemoMode(), "Demo mode should be enabled when property is set");
    }

    @Test
    void testDemoModeNotEnabledWithFalseValue() {
        System.setProperty("vericrop.demoMode", "false");
        AuthenticationService service = new AuthenticationService(dataSource);
        assertFalse(service.isDemoMode(), "Demo mode should not be enabled when property is 'false'");
    }

    @Test
    void testSetDemoModeProgrammatically() {
        AuthenticationService service = new AuthenticationService(dataSource);
        assertFalse(service.isDemoMode());
        
        service.setDemoMode(true);
        assertTrue(service.isDemoMode());
        
        service.setDemoMode(false);
        assertFalse(service.isDemoMode());
    }

    // ==================== Login Tests - No Database ====================

    @Test
    void testLoginFailsWithoutDatabaseWhenDemoModeDisabled() {
        // No demo mode enabled, no database
        AuthenticationService service = new AuthenticationService();
        
        boolean result = service.login("anyuser", "anypassword");
        
        assertFalse(result, "Login should fail without database when demo mode is disabled");
        assertFalse(service.isAuthenticated());
    }

    @Test
    void testLoginSucceedsWithDemoModeEnabled() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService();
        
        boolean result = service.login("admin", "admin123");
        
        assertTrue(result, "Demo login should succeed with correct demo credentials");
        assertTrue(service.isAuthenticated());
        assertEquals("admin", service.getCurrentUser());
        assertEquals("ADMIN", service.getCurrentRole());
    }

    @Test
    void testDemoLoginFailsWithWrongPassword() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService();
        
        boolean result = service.login("admin", "wrongpassword");
        
        assertFalse(result, "Demo login should fail with wrong password");
        assertFalse(service.isAuthenticated());
    }

    @Test
    void testDemoLoginFailsWithUnknownUser() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService();
        
        boolean result = service.login("unknownuser", "somepassword");
        
        assertFalse(result, "Demo login should fail with unknown username");
        assertFalse(service.isAuthenticated());
    }

    @Test
    void testDemoLoginFarmerAccount() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService();
        
        boolean result = service.login("farmer", "farmer123");
        
        assertTrue(result);
        assertEquals("FARMER", service.getCurrentRole());
    }

    @Test
    void testDemoLoginSupplierAccount() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService();
        
        boolean result = service.login("supplier", "supplier123");
        
        assertTrue(result);
        assertEquals("SUPPLIER", service.getCurrentRole());
    }

    @Test
    void testDemoLoginConsumerAccount() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService();
        
        boolean result = service.login("consumer", "consumer123");
        
        assertTrue(result);
        assertEquals("CONSUMER", service.getCurrentRole());
    }

    // ==================== Login Validation Tests ====================

    @Test
    void testLoginFailsWithEmptyUsername() {
        AuthenticationService service = new AuthenticationService(dataSource);
        
        boolean result = service.login("", "password");
        
        assertFalse(result, "Login should fail with empty username");
    }

    @Test
    void testLoginFailsWithNullUsername() {
        AuthenticationService service = new AuthenticationService(dataSource);
        
        boolean result = service.login(null, "password");
        
        assertFalse(result, "Login should fail with null username");
    }

    @Test
    void testLoginFailsWithEmptyPassword() {
        AuthenticationService service = new AuthenticationService(dataSource);
        
        boolean result = service.login("user", "");
        
        assertFalse(result, "Login should fail with empty password");
    }

    @Test
    void testLoginFailsWithNullPassword() {
        AuthenticationService service = new AuthenticationService(dataSource);
        
        boolean result = service.login("user", null);
        
        assertFalse(result, "Login should fail with null password");
    }

    // ==================== Database Authentication Tests ====================

    @Test
    void testLoginFailsWithUserNotFound() throws SQLException {
        AuthenticationService service = new AuthenticationService(dataSource);
        
        when(resultSet.next()).thenReturn(false);
        
        boolean result = service.login("nonexistent", "password");
        
        assertFalse(result, "Login should fail when user is not found");
        assertFalse(service.isAuthenticated());
    }

    @Test
    void testLoginFailsWithDatabaseError() throws SQLException {
        AuthenticationService service = new AuthenticationService(dataSource);
        
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));
        
        boolean result = service.login("user", "password");
        
        assertFalse(result, "Login should fail on database error when demo mode is disabled");
        assertFalse(service.isAuthenticated());
    }

    @Test
    void testDatabaseErrorFallsBackToDemoModeWhenEnabled() throws SQLException {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService(dataSource);
        
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));
        
        // Should fall back to demo mode
        boolean result = service.login("admin", "admin123");
        
        assertTrue(result, "Login should fall back to demo mode when database fails and demo mode is enabled");
        assertTrue(service.isAuthenticated());
    }

    // ==================== Session Management Tests ====================

    @Test
    void testLogoutClearsSession() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService();
        
        service.login("admin", "admin123");
        assertTrue(service.isAuthenticated());
        
        service.logout();
        
        assertFalse(service.isAuthenticated());
        assertNull(service.getCurrentUser());
        assertNull(service.getCurrentRole());
    }

    @Test
    void testSessionDataManagement() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService();
        
        service.login("admin", "admin123");
        
        service.setSessionData("testKey", "testValue");
        assertEquals("testValue", service.getSessionData("testKey"));
        
        service.logout();
        assertNull(service.getSessionData("testKey"), "Session data should be cleared on logout");
    }

    // ==================== Role Check Tests ====================

    @Test
    void testHasRoleMethod() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService();
        
        service.login("admin", "admin123");
        
        assertTrue(service.hasRole("ADMIN"));
        assertTrue(service.hasRole("admin")); // Case insensitive
        assertFalse(service.hasRole("FARMER"));
    }

    @Test
    void testRoleConvenienceMethods() {
        System.setProperty("vericrop.demoMode", "true");
        AuthenticationService service = new AuthenticationService();
        
        service.login("farmer", "farmer123");
        assertTrue(service.isFarmer());
        assertFalse(service.isAdmin());
        assertFalse(service.isConsumer());
        assertFalse(service.isSupplier());
    }
}
