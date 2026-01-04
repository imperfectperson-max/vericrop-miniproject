package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.vericrop.gui.api.AnalyticsRestController.BatchInfo;
import org.vericrop.gui.api.AnalyticsRestController.ComplianceInfo;
import org.vericrop.gui.services.JwtService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for AnalyticsRestController admin authentication.
 * Tests that non-admin users cannot access analytics endpoints.
 */
class AnalyticsRestControllerAuthTest {
    
    @Mock
    private JwtService jwtService;
    
    private AnalyticsRestController controller;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new AnalyticsRestController(jwtService);
    }
    
    @Test
    void testGetRecentBatches_NoAuth_Unauthorized() {
        // When: No auth header provided
        ResponseEntity<?> response = controller.getRecentBatches(null);
        
        // Then: Should return 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("Authentication required", body.get("error"));
    }
    
    @Test
    void testGetRecentBatches_InvalidToken_Unauthorized() {
        // Given: Invalid token
        String invalidToken = "Bearer invalid_token_12345";
        when(jwtService.isTokenValid("invalid_token_12345")).thenReturn(false);
        
        // When: Request with invalid token
        ResponseEntity<?> response = controller.getRecentBatches(invalidToken);
        
        // Then: Should return 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("Invalid token", body.get("error"));
    }
    
    @Test
    void testGetRecentBatches_NonAdminUser_Forbidden() {
        // Given: Valid token but non-admin role
        String token = "Bearer valid_token_12345";
        when(jwtService.isTokenValid("valid_token_12345")).thenReturn(true);
        when(jwtService.extractRole("valid_token_12345")).thenReturn("PRODUCER");
        
        // When: Non-admin user tries to access analytics
        ResponseEntity<?> response = controller.getRecentBatches(token);
        
        // Then: Should return 403 Forbidden
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("Access denied", body.get("error"));
        assertTrue(body.get("message").toString().contains("Admin role required"));
    }
    
    @Test
    void testGetRecentBatches_AdminUser_Success() {
        // Given: Valid admin token
        String token = "Bearer admin_token_12345";
        when(jwtService.isTokenValid("admin_token_12345")).thenReturn(true);
        when(jwtService.extractRole("admin_token_12345")).thenReturn("ADMIN");
        
        // When: Admin user accesses analytics
        ResponseEntity<?> response = controller.getRecentBatches(token);
        
        // Then: Should return 200 OK
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    
    @Test
    void testUpdateBatchStatus_NonAdmin_Forbidden() {
        // Given: Valid non-admin token
        String token = "Bearer producer_token_12345";
        when(jwtService.isTokenValid("producer_token_12345")).thenReturn(true);
        when(jwtService.extractRole("producer_token_12345")).thenReturn("PRODUCER");
        
        // When: Non-admin tries to update batch status
        ResponseEntity<?> response = controller.updateBatchStatus(token, "BATCH_001", "DELIVERED");
        
        // Then: Should return 403 Forbidden
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
    
    @Test
    void testUpdateComplianceStatus_AdminUser_Success() {
        // Given: Valid admin token and compliance info
        String token = "Bearer admin_token_12345";
        when(jwtService.isTokenValid("admin_token_12345")).thenReturn(true);
        when(jwtService.extractRole("admin_token_12345")).thenReturn("ADMIN");
        
        ComplianceInfo compliance = new ComplianceInfo(true, 0);
        
        // When: Admin updates compliance
        ResponseEntity<?> response = controller.updateComplianceStatus(token, "NEW_BATCH", compliance);
        
        // Then: Should return 200 OK
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    
    @Test
    void testUpdateRecentBatches_ConsumerUser_Forbidden() {
        // Given: Valid consumer token
        String token = "Bearer consumer_token_12345";
        when(jwtService.isTokenValid("consumer_token_12345")).thenReturn(true);
        when(jwtService.extractRole("consumer_token_12345")).thenReturn("CONSUMER");
        
        BatchInfo batchInfo = new BatchInfo();
        batchInfo.setBatchId("TEST_BATCH_001");
        
        // When: Consumer tries to update batches
        ResponseEntity<?> response = controller.updateRecentBatches(token, batchInfo);
        
        // Then: Should return 403 Forbidden
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
