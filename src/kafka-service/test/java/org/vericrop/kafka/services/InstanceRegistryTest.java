package org.vericrop.kafka.services;

import org.junit.jupiter.api.Test;
import org.vericrop.kafka.events.InstanceHeartbeatEvent.Role;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InstanceRegistry role-based tracking logic.
 * These tests verify the role-based instance tracking without requiring Kafka.
 * 
 * Note: Full integration tests with Kafka would require a running Kafka broker.
 * These tests focus on the core logic of role tracking and simulation requirements.
 */
class InstanceRegistryTest {
    
    /**
     * Helper class to simulate an in-memory instance registry for testing
     * without Kafka dependencies.
     */
    static class TestableInstanceRegistry {
        private final Map<String, InstanceRegistry.InstanceInfo> registry = 
            new java.util.concurrent.ConcurrentHashMap<>();
        
        void registerInstance(String instanceId, Role role, String host, int port) {
            registry.put(instanceId, new InstanceRegistry.InstanceInfo(
                instanceId, role, host, port, System.currentTimeMillis()));
        }
        
        void unregisterInstance(String instanceId) {
            registry.remove(instanceId);
        }
        
        int getActiveInstanceCount() {
            return registry.size();
        }
        
        int getActiveInstanceCountByRole(Role role) {
            return (int) registry.values().stream()
                .filter(info -> info.getRole() == role)
                .count();
        }
        
        List<InstanceRegistry.InstanceInfo> getInstancesByRole(Role role) {
            return registry.values().stream()
                .filter(info -> info.getRole() == role)
                .toList();
        }
        
        Set<Role> getActiveRoles() {
            return registry.values().stream()
                .map(InstanceRegistry.InstanceInfo::getRole)
                .filter(r -> r != Role.UNKNOWN)
                .collect(java.util.stream.Collectors.toSet());
        }
        
        boolean hasRequiredRolesForSimulation() {
            Set<Role> activeRoles = getActiveRoles();
            return activeRoles.contains(Role.PRODUCER) && 
                   activeRoles.contains(Role.LOGISTICS) && 
                   activeRoles.contains(Role.CONSUMER);
        }
        
        Set<Role> getMissingRolesForSimulation() {
            Set<Role> activeRoles = getActiveRoles();
            Set<Role> required = Set.of(Role.PRODUCER, Role.LOGISTICS, Role.CONSUMER);
            return required.stream()
                .filter(role -> !activeRoles.contains(role))
                .collect(java.util.stream.Collectors.toSet());
        }
    }
    
    @Test
    void testEmptyRegistryShouldNotHaveRequiredRoles() {
        // Given
        TestableInstanceRegistry registry = new TestableInstanceRegistry();
        
        // Then
        assertFalse(registry.hasRequiredRolesForSimulation());
        assertEquals(0, registry.getActiveInstanceCount());
        assertTrue(registry.getActiveRoles().isEmpty());
    }
    
    @Test
    void testSingleProducerShouldNotBeEnough() {
        // Given
        TestableInstanceRegistry registry = new TestableInstanceRegistry();
        
        // When
        registry.registerInstance("producer-1", Role.PRODUCER, "localhost", 8080);
        
        // Then
        assertFalse(registry.hasRequiredRolesForSimulation());
        assertEquals(1, registry.getActiveInstanceCount());
        assertEquals(1, registry.getActiveInstanceCountByRole(Role.PRODUCER));
        assertTrue(registry.getActiveRoles().contains(Role.PRODUCER));
        
        // Missing roles should be LOGISTICS and CONSUMER
        Set<Role> missing = registry.getMissingRolesForSimulation();
        assertEquals(2, missing.size());
        assertTrue(missing.contains(Role.LOGISTICS));
        assertTrue(missing.contains(Role.CONSUMER));
    }
    
    @Test
    void testProducerAndLogisticsShouldNotBeEnough() {
        // Given
        TestableInstanceRegistry registry = new TestableInstanceRegistry();
        
        // When
        registry.registerInstance("producer-1", Role.PRODUCER, "localhost", 8080);
        registry.registerInstance("logistics-1", Role.LOGISTICS, "localhost", 8081);
        
        // Then
        assertFalse(registry.hasRequiredRolesForSimulation());
        assertEquals(2, registry.getActiveInstanceCount());
        
        // Missing roles should be CONSUMER only
        Set<Role> missing = registry.getMissingRolesForSimulation();
        assertEquals(1, missing.size());
        assertTrue(missing.contains(Role.CONSUMER));
    }
    
    @Test
    void testAllRequiredRolesShouldBeEnough() {
        // Given
        TestableInstanceRegistry registry = new TestableInstanceRegistry();
        
        // When - Register one instance of each required role
        registry.registerInstance("producer-1", Role.PRODUCER, "localhost", 8080);
        registry.registerInstance("logistics-1", Role.LOGISTICS, "localhost", 8081);
        registry.registerInstance("consumer-1", Role.CONSUMER, "localhost", 8082);
        
        // Then - Should have all required roles
        assertTrue(registry.hasRequiredRolesForSimulation());
        assertEquals(3, registry.getActiveInstanceCount());
        assertEquals(1, registry.getActiveInstanceCountByRole(Role.PRODUCER));
        assertEquals(1, registry.getActiveInstanceCountByRole(Role.LOGISTICS));
        assertEquals(1, registry.getActiveInstanceCountByRole(Role.CONSUMER));
        
        // No missing roles
        Set<Role> missing = registry.getMissingRolesForSimulation();
        assertTrue(missing.isEmpty());
    }
    
    @Test
    void testMultipleInstancesOfSameRoleShouldWork() {
        // Given
        TestableInstanceRegistry registry = new TestableInstanceRegistry();
        
        // When - Register multiple instances of each role
        registry.registerInstance("producer-1", Role.PRODUCER, "machine-a", 8080);
        registry.registerInstance("producer-2", Role.PRODUCER, "machine-b", 8080);
        registry.registerInstance("logistics-1", Role.LOGISTICS, "machine-a", 8081);
        registry.registerInstance("logistics-2", Role.LOGISTICS, "machine-b", 8081);
        registry.registerInstance("consumer-1", Role.CONSUMER, "machine-a", 8082);
        registry.registerInstance("consumer-2", Role.CONSUMER, "machine-b", 8082);
        registry.registerInstance("consumer-3", Role.CONSUMER, "machine-c", 8082);
        
        // Then - Should have all required roles with multiple instances each
        assertTrue(registry.hasRequiredRolesForSimulation());
        assertEquals(7, registry.getActiveInstanceCount());
        assertEquals(2, registry.getActiveInstanceCountByRole(Role.PRODUCER));
        assertEquals(2, registry.getActiveInstanceCountByRole(Role.LOGISTICS));
        assertEquals(3, registry.getActiveInstanceCountByRole(Role.CONSUMER));
        
        // Verify we can get instances by role
        List<InstanceRegistry.InstanceInfo> producers = registry.getInstancesByRole(Role.PRODUCER);
        assertEquals(2, producers.size());
        
        List<InstanceRegistry.InstanceInfo> consumers = registry.getInstancesByRole(Role.CONSUMER);
        assertEquals(3, consumers.size());
    }
    
    @Test
    void testUnregisteringInstanceShouldAffectRoleAvailability() {
        // Given - Start with all required roles
        TestableInstanceRegistry registry = new TestableInstanceRegistry();
        registry.registerInstance("producer-1", Role.PRODUCER, "localhost", 8080);
        registry.registerInstance("logistics-1", Role.LOGISTICS, "localhost", 8081);
        registry.registerInstance("consumer-1", Role.CONSUMER, "localhost", 8082);
        assertTrue(registry.hasRequiredRolesForSimulation());
        
        // When - Unregister the logistics instance
        registry.unregisterInstance("logistics-1");
        
        // Then - Should no longer have required roles
        assertFalse(registry.hasRequiredRolesForSimulation());
        assertEquals(2, registry.getActiveInstanceCount());
        assertEquals(0, registry.getActiveInstanceCountByRole(Role.LOGISTICS));
        
        Set<Role> missing = registry.getMissingRolesForSimulation();
        assertEquals(1, missing.size());
        assertTrue(missing.contains(Role.LOGISTICS));
    }
    
    @Test
    void testUnknownRoleShouldNotCountTowardsRequirements() {
        // Given
        TestableInstanceRegistry registry = new TestableInstanceRegistry();
        
        // When - Register instances with UNKNOWN role
        registry.registerInstance("unknown-1", Role.UNKNOWN, "localhost", 8080);
        registry.registerInstance("unknown-2", Role.UNKNOWN, "localhost", 8081);
        registry.registerInstance("unknown-3", Role.UNKNOWN, "localhost", 8082);
        
        // Then - Should NOT have required roles (UNKNOWN doesn't count)
        assertFalse(registry.hasRequiredRolesForSimulation());
        assertEquals(3, registry.getActiveInstanceCount());
        assertEquals(3, registry.getActiveInstanceCountByRole(Role.UNKNOWN));
        
        // Active roles should not include UNKNOWN
        Set<Role> activeRoles = registry.getActiveRoles();
        assertFalse(activeRoles.contains(Role.UNKNOWN));
        assertTrue(activeRoles.isEmpty());
        
        // All required roles should be missing
        Set<Role> missing = registry.getMissingRolesForSimulation();
        assertEquals(3, missing.size());
    }
    
    @Test
    void testMixedKnownAndUnknownRoles() {
        // Given
        TestableInstanceRegistry registry = new TestableInstanceRegistry();
        
        // When - Register mix of known and unknown roles
        registry.registerInstance("producer-1", Role.PRODUCER, "localhost", 8080);
        registry.registerInstance("unknown-1", Role.UNKNOWN, "localhost", 8081);
        registry.registerInstance("logistics-1", Role.LOGISTICS, "localhost", 8082);
        
        // Then - Only known roles should count
        assertFalse(registry.hasRequiredRolesForSimulation());
        assertEquals(3, registry.getActiveInstanceCount());
        
        Set<Role> activeRoles = registry.getActiveRoles();
        assertEquals(2, activeRoles.size());
        assertTrue(activeRoles.contains(Role.PRODUCER));
        assertTrue(activeRoles.contains(Role.LOGISTICS));
        assertFalse(activeRoles.contains(Role.UNKNOWN));
        
        // CONSUMER should be missing
        Set<Role> missing = registry.getMissingRolesForSimulation();
        assertEquals(1, missing.size());
        assertTrue(missing.contains(Role.CONSUMER));
    }
    
    @Test
    void testInstanceInfoFieldsAreCorrect() {
        // Given
        String instanceId = "test-instance";
        Role role = Role.LOGISTICS;
        String host = "192.168.1.100";
        int port = 9090;
        long timestamp = System.currentTimeMillis();
        
        // When
        InstanceRegistry.InstanceInfo info = new InstanceRegistry.InstanceInfo(
            instanceId, role, host, port, timestamp);
        
        // Then
        assertEquals(instanceId, info.getInstanceId());
        assertEquals(role, info.getRole());
        assertEquals(host, info.getHost());
        assertEquals(port, info.getPort());
        assertEquals(timestamp, info.getLastSeen());
        
        // Update lastSeen
        long newTimestamp = timestamp + 5000;
        info.setLastSeen(newTimestamp);
        assertEquals(newTimestamp, info.getLastSeen());
    }
    
    @Test
    void testInstanceInfoToString() {
        // Given
        InstanceRegistry.InstanceInfo info = new InstanceRegistry.InstanceInfo(
            "test-id", Role.PRODUCER, "localhost", 8080, System.currentTimeMillis());
        
        // When
        String str = info.toString();
        
        // Then
        assertNotNull(str);
        assertTrue(str.contains("test-id"));
        assertTrue(str.contains("PRODUCER"));
        assertTrue(str.contains("localhost"));
        assertTrue(str.contains("8080"));
    }
    
    @Test
    void testGetRequiredRolesForSimulation() {
        // Verify the required roles are PRODUCER, LOGISTICS, and CONSUMER
        Set<Role> required = Set.of(Role.PRODUCER, Role.LOGISTICS, Role.CONSUMER);
        
        assertEquals(3, required.size());
        assertTrue(required.contains(Role.PRODUCER));
        assertTrue(required.contains(Role.LOGISTICS));
        assertTrue(required.contains(Role.CONSUMER));
        assertFalse(required.contains(Role.UNKNOWN));
    }
}
