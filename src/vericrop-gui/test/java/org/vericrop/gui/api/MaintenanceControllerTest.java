package org.vericrop.gui.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.vericrop.gui.services.MaintenanceService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MaintenanceController.
 * Tests API endpoints for maintenance operations.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceControllerTest {
    
    @Mock
    private MaintenanceService maintenanceService;
    
    private MaintenanceController controller;
    
    @BeforeEach
    void setUp() {
        controller = new MaintenanceController(maintenanceService);
    }
    
    // ==================== GET /api/maintenance/counts ====================
    
    @Test
    void testGetRecordCounts_Success() {
        // Arrange
        Map<String, Object> counts = new HashMap<>();
        counts.put("simulations", 10);
        counts.put("batches", 50);
        when(maintenanceService.getRecordCounts()).thenReturn(counts);
        when(maintenanceService.isMaintenanceModeEnabled()).thenReturn(false);
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.getRecordCounts();
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10, response.getBody().get("simulations"));
        assertEquals(50, response.getBody().get("batches"));
        assertFalse((Boolean) response.getBody().get("maintenance_mode_enabled"));
    }
    
    // ==================== GET /api/maintenance/status ====================
    
    @Test
    void testGetMaintenanceStatus_MaintenanceModeDisabled() {
        // Arrange
        when(maintenanceService.isMaintenanceModeEnabled()).thenReturn(false);
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.getMaintenanceStatus();
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("maintenance", response.getBody().get("service"));
        assertEquals("UP", response.getBody().get("status"));
        assertFalse((Boolean) response.getBody().get("maintenance_mode_enabled"));
        assertTrue((Boolean) response.getBody().get("confirmation_token_required"));
    }
    
    @Test
    void testGetMaintenanceStatus_MaintenanceModeEnabled() {
        // Arrange
        when(maintenanceService.isMaintenanceModeEnabled()).thenReturn(true);
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.getMaintenanceStatus();
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("maintenance_mode_enabled"));
        assertFalse((Boolean) response.getBody().get("confirmation_token_required"));
    }
    
    // ==================== POST /api/maintenance/delete-all ====================
    
    @Test
    void testDeleteAllRecords_Success() {
        // Arrange
        Map<String, Object> details = new HashMap<>();
        details.put("simulations_backed_up", 5);
        details.put("batches_backed_up", 20);
        
        MaintenanceService.MaintenanceResult successResult = 
            MaintenanceService.MaintenanceResult.success(5, 20, "/backup/path", details);
        
        when(maintenanceService.deleteAllRecordsWithBackup(eq("CONFIRM_DELETE_ALL_RECORDS")))
            .thenReturn(successResult);
        
        Map<String, Object> request = new HashMap<>();
        request.put("confirmation_token", "CONFIRM_DELETE_ALL_RECORDS");
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.deleteAllRecords(request);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals(5, response.getBody().get("simulations_deleted"));
        assertEquals(20, response.getBody().get("batches_deleted"));
        assertEquals("/backup/path", response.getBody().get("backup_path"));
    }
    
    @Test
    void testDeleteAllRecords_NoConfirmation() {
        // Arrange
        MaintenanceService.MaintenanceResult errorResult = 
            MaintenanceService.MaintenanceResult.error(
                "Maintenance operation not allowed. Set MAINTENANCE_MODE=true or provide confirmation token.");
        
        when(maintenanceService.deleteAllRecordsWithBackup(isNull()))
            .thenReturn(errorResult);
        
        // Act - no request body
        ResponseEntity<Map<String, Object>> response = controller.deleteAllRecords(null);
        
        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }
    
    @Test
    void testDeleteAllRecords_WrongConfirmationToken() {
        // Arrange
        MaintenanceService.MaintenanceResult errorResult = 
            MaintenanceService.MaintenanceResult.error(
                "Maintenance operation not allowed. Set MAINTENANCE_MODE=true or provide confirmation token.");
        
        when(maintenanceService.deleteAllRecordsWithBackup(eq("WRONG_TOKEN")))
            .thenReturn(errorResult);
        
        Map<String, Object> request = new HashMap<>();
        request.put("confirmation_token", "WRONG_TOKEN");
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.deleteAllRecords(request);
        
        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }
    
    // ==================== POST /api/maintenance/dry-run ====================
    
    @Test
    void testDryRun_Success() {
        // Arrange
        Map<String, Object> counts = new HashMap<>();
        counts.put("simulations", 10);
        counts.put("batches", 50);
        when(maintenanceService.getRecordCounts()).thenReturn(counts);
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.dryRun();
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("dry_run"));
        assertEquals(10, response.getBody().get("would_delete_simulations"));
        assertEquals(50, response.getBody().get("would_delete_batches"));
        assertTrue(response.getBody().get("message").toString().contains("No records were deleted"));
    }
    
    // ==================== GET /api/maintenance/backups ====================
    
    @Test
    void testListBackups_Success() {
        // Arrange - use descriptive backup names
        List<String> backups = List.of("backup_1", "backup_2", "backup_3");
        when(maintenanceService.listBackups()).thenReturn(backups);
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.listBackups();
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(backups, response.getBody().get("backups"));
        assertEquals(3, response.getBody().get("count"));
        assertEquals("backups/", response.getBody().get("backup_directory"));
    }
    
    @Test
    void testListBackups_Empty() {
        // Arrange
        when(maintenanceService.listBackups()).thenReturn(List.of());
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.listBackups();
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), response.getBody().get("backups"));
        assertEquals(0, response.getBody().get("count"));
    }
}
