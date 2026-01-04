package org.vericrop.kafka.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.vericrop.kafka.KafkaConfig;
import org.vericrop.kafka.events.InstanceHeartbeatEvent;
import org.vericrop.kafka.events.InstanceHeartbeatEvent.Role;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Service for tracking running application instances using Kafka.
 * Each instance sends periodic heartbeats to the instance-registry topic.
 * The service maintains a registry of active instances by their unique ID and role.
 * 
 * This supports multi-instance coordination by:
 * - Tracking instances by unique identifier (role + host + port or UUID)
 * - Grouping instances by role (PRODUCER, LOGISTICS, CONSUMER)
 * - Allowing multiple instances of the same role to run concurrently
 * - Providing role-based queries for simulation start validation
 * 
 * For simulations to start, at least one instance of each required role must be present.
 */
public class InstanceRegistry implements AutoCloseable {
    
    /** Default heartbeat interval in milliseconds */
    private static final long HEARTBEAT_INTERVAL_MS = 5000; // 5 secondspr
    
    /** Time after which an instance is considered dead if no heartbeat received */
    private static final long INSTANCE_TIMEOUT_MS = 15000; // 15 seconds
    
    /** Minimum number of instances required for coordinated simulation start (legacy) */
    private static final int MIN_INSTANCES_FOR_COORDINATION = 3;
    
    /** Required roles for simulation coordination */
    private static final Set<Role> REQUIRED_ROLES_FOR_SIMULATION = Set.of(
        Role.PRODUCER, Role.LOGISTICS, Role.CONSUMER
    );
    
    private final String instanceId;
    private final Role role;
    private final String host;
    private final int port;
    private final KafkaProducer<String, String> producer;
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final Map<String, InstanceInfo> instanceRegistry;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    
    /**
     * Information about a registered instance.
     */
    public static class InstanceInfo {
        private final String instanceId;
        private final Role role;
        private final String host;
        private final int port;
        private volatile long lastSeen;
        
        public InstanceInfo(String instanceId, Role role, String host, int port, long lastSeen) {
            this.instanceId = instanceId;
            this.role = role;
            this.host = host;
            this.port = port;
            this.lastSeen = lastSeen;
        }
        
        public String getInstanceId() { return instanceId; }
        public Role getRole() { return role; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public long getLastSeen() { return lastSeen; }
        public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
        
        @Override
        public String toString() {
            return String.format("InstanceInfo{id=%s, role=%s, host=%s, port=%d}", 
                instanceId, role, host, port);
        }
    }
    
    /**
     * Create a new instance registry with a random instance ID and UNKNOWN role.
     * For backward compatibility with existing code.
     */
    public InstanceRegistry() {
        this(UUID.randomUUID().toString(), Role.UNKNOWN, null, 0);
    }
    
    /**
     * Create a new instance registry with a specific instance ID and UNKNOWN role.
     * For backward compatibility with existing code.
     * 
     * @param instanceId Unique identifier for this instance
     */
    public InstanceRegistry(String instanceId) {
        this(instanceId, Role.UNKNOWN, null, 0);
    }
    
    /**
     * Create a new instance registry with a specific role.
     * 
     * @param role The role of this instance (PRODUCER, LOGISTICS, CONSUMER)
     */
    public InstanceRegistry(Role role) {
        this(UUID.randomUUID().toString(), role, null, 0);
    }
    
    /**
     * Create a new instance registry with a specific role, host, and port.
     * 
     * @param role The role of this instance
     * @param host The host/IP address of this instance
     * @param port The port number of this instance
     */
    public InstanceRegistry(Role role, String host, int port) {
        this(UUID.randomUUID().toString(), role, host, port);
    }
    
    /**
     * Create a new instance registry with full configuration.
     * 
     * @param instanceId Unique identifier for this instance
     * @param role The role of this instance
     * @param host The host/IP address of this instance
     * @param port The port number of this instance
     */
    public InstanceRegistry(String instanceId, Role role, String host, int port) {
        this.instanceId = instanceId;
        this.role = role != null ? role : Role.UNKNOWN;
        this.host = host;
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.instanceRegistry = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Initialize Kafka producer and consumer
        this.producer = new KafkaProducer<>(KafkaConfig.getProducerProperties());
        this.consumer = new KafkaConsumer<>(KafkaConfig.getConsumerProperties(
            "instance-registry-" + instanceId));
    }
    
    /**
     * Start the instance registry.
     * Begins sending heartbeats and consuming heartbeats from other instances.
     */
    public void start() {
        if (running.getAndSet(true)) {
            return; // Already running
        }
        
        System.out.println("🔄 Starting InstanceRegistry for instance: " + instanceId + " (role: " + role + ")");
        
        // Start heartbeat sender
        scheduler.scheduleAtFixedRate(
            this::sendHeartbeat,
            0,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        // Start heartbeat consumer in background thread
        scheduler.submit(this::consumeHeartbeats);
        
        // Start cleanup task to remove stale instances
        scheduler.scheduleAtFixedRate(
            this::cleanupStaleInstances,
            INSTANCE_TIMEOUT_MS,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        System.out.println("✅ InstanceRegistry started for instance: " + instanceId + " (role: " + role + ")");
    }
    
    /**
     * Send a heartbeat event to indicate this instance is alive.
     */
    private void sendHeartbeat() {
        try {
            InstanceHeartbeatEvent event = new InstanceHeartbeatEvent(instanceId, role, host, port);
            String json = objectMapper.writeValueAsString(event);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaConfig.TOPIC_INSTANCE_REGISTRY,
                instanceId,
                json
            );
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("❌ Failed to send heartbeat: " + exception.getMessage());
                }
            });
            
            // Also update our own entry in the registry
            instanceRegistry.put(instanceId, new InstanceInfo(instanceId, role, host, port, System.currentTimeMillis()));
            
        } catch (Exception e) {
            System.err.println("❌ Error sending heartbeat: " + e.getMessage());
        }
    }
    
    /**
     * Consume heartbeat events from other instances.
     */
    private void consumeHeartbeats() {
        consumer.subscribe(Collections.singletonList(KafkaConfig.TOPIC_INSTANCE_REGISTRY));
        
        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        InstanceHeartbeatEvent event = objectMapper.readValue(
                            record.value(), InstanceHeartbeatEvent.class);
                        
                        if (event.isAlive()) {
                            InstanceInfo info = new InstanceInfo(
                                event.getInstanceId(),
                                event.getRoleEnum(),
                                event.getHost(),
                                event.getPort(),
                                event.getTimestamp()
                            );
                            instanceRegistry.put(event.getInstanceId(), info);
                        } else if (event.isShutdown()) {
                            InstanceInfo removed = instanceRegistry.remove(event.getInstanceId());
                            if (removed != null) {
                                System.out.println("📤 Instance shutdown: " + event.getInstanceId() + 
                                    " (role: " + removed.getRole() + ")");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error parsing heartbeat event: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("❌ Error consuming heartbeats: " + e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Remove instances that haven't sent a heartbeat recently.
     * Note: The current instance is never removed as stale because its heartbeat
     * is refreshed in sendHeartbeat() before this cleanup runs.
     */
    private void cleanupStaleInstances() {
        long now = System.currentTimeMillis();
        instanceRegistry.entrySet().removeIf(entry -> {
            // Never remove ourselves - our heartbeat should always be fresh
            if (entry.getKey().equals(instanceId)) {
                return false;
            }
            boolean isStale = (now - entry.getValue().getLastSeen()) > INSTANCE_TIMEOUT_MS;
            if (isStale) {
                System.out.println("🗑️ Removing stale instance: " + entry.getKey() + 
                    " (role: " + entry.getValue().getRole() + ")");
            }
            return isStale;
        });
    }
    
    /**
     * Get the number of active instances (including this one).
     * 
     * @return Count of active instances
     */
    public int getActiveInstanceCount() {
        cleanupStaleInstances();
        return instanceRegistry.size();
    }
    
    /**
     * Get the number of active instances for a specific role.
     * 
     * @param role The role to count instances for
     * @return Count of active instances with the specified role
     */
    public int getActiveInstanceCountByRole(Role role) {
        cleanupStaleInstances();
        return (int) instanceRegistry.values().stream()
            .filter(info -> info.getRole() == role)
            .count();
    }
    
    /**
     * Get all active instances for a specific role.
     * 
     * @param role The role to get instances for
     * @return Immutable list of instance info for the specified role
     */
    public List<InstanceInfo> getInstancesByRole(Role role) {
        cleanupStaleInstances();
        return instanceRegistry.values().stream()
            .filter(info -> info.getRole() == role)
            .toList();
    }
    
    /**
     * Get all active instances grouped by role.
     * 
     * @return Immutable map of role to list of instances
     */
    public Map<Role, List<InstanceInfo>> getInstancesByRoleMap() {
        cleanupStaleInstances();
        Map<Role, List<InstanceInfo>> groupedMap = instanceRegistry.values().stream()
            .collect(Collectors.groupingBy(InstanceInfo::getRole));
        return Map.copyOf(groupedMap);
    }
    
    /**
     * Get the set of roles that currently have at least one active instance.
     * 
     * @return Set of active roles
     */
    public Set<Role> getActiveRoles() {
        cleanupStaleInstances();
        return instanceRegistry.values().stream()
            .map(InstanceInfo::getRole)
            .filter(r -> r != Role.UNKNOWN)
            .collect(Collectors.toSet());
    }
    
    /**
     * Check if all required roles for simulation are present.
     * Required roles are: PRODUCER, LOGISTICS, CONSUMER.
     * 
     * @return true if at least one instance of each required role is active
     */
    public boolean hasRequiredRolesForSimulation() {
        Set<Role> activeRoles = getActiveRoles();
        return activeRoles.containsAll(REQUIRED_ROLES_FOR_SIMULATION);
    }
    
    /**
     * Get the missing roles required for simulation.
     * 
     * @return Set of roles that are required but not currently active
     */
    public Set<Role> getMissingRolesForSimulation() {
        Set<Role> activeRoles = getActiveRoles();
        return REQUIRED_ROLES_FOR_SIMULATION.stream()
            .filter(role -> !activeRoles.contains(role))
            .collect(Collectors.toSet());
    }
    
    /**
     * Check if enough instances are running for coordinated simulation start.
     * This now checks for required roles instead of just counting instances.
     * 
     * @return true if all required roles (PRODUCER, LOGISTICS, CONSUMER) are present
     */
    public boolean hasEnoughInstances() {
        // First check if we have required roles
        if (hasRequiredRolesForSimulation()) {
            return true;
        }
        // Fallback to legacy count-based check for backward compatibility
        // (for instances that don't specify a role)
        return getActiveInstanceCount() >= MIN_INSTANCES_FOR_COORDINATION;
    }
    
    /**
     * Get the minimum number of instances required for coordinated simulation.
     * 
     * @return Minimum instance count (legacy)
     */
    public int getMinInstancesRequired() {
        return MIN_INSTANCES_FOR_COORDINATION;
    }
    
    /**
     * Get the required roles for simulation.
     * 
     * @return Set of required roles
     */
    public Set<Role> getRequiredRolesForSimulation() {
        return new HashSet<>(REQUIRED_ROLES_FOR_SIMULATION);
    }
    
    /**
     * Get this instance's unique ID.
     * 
     * @return Instance ID
     */
    public String getInstanceId() {
        return instanceId;
    }
    
    /**
     * Get this instance's role.
     * 
     * @return Instance role
     */
    public Role getRole() {
        return role;
    }
    
    /**
     * Get an immutable copy of all registered instances.
     * 
     * @return Immutable map of instance ID to instance info
     */
    public Map<String, InstanceInfo> getAllInstances() {
        cleanupStaleInstances();
        return Map.copyOf(instanceRegistry);
    }
    
    /**
     * Send a shutdown heartbeat and stop the registry.
     */
    @Override
    public void close() {
        if (!running.getAndSet(false)) {
            return; // Already stopped
        }
        
        System.out.println("🛑 Stopping InstanceRegistry for instance: " + instanceId + " (role: " + role + ")");
        
        // Send shutdown heartbeat
        try {
            InstanceHeartbeatEvent shutdownEvent = InstanceHeartbeatEvent.shutdown(instanceId, role);
            String json = objectMapper.writeValueAsString(shutdownEvent);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaConfig.TOPIC_INSTANCE_REGISTRY,
                instanceId,
                json
            );
            
            producer.send(record).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("⚠️ Could not send shutdown heartbeat: " + e.getMessage());
        }
        
        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Close Kafka clients
        if (consumer != null) {
            consumer.wakeup();
            consumer.close();
        }
        if (producer != null) {
            producer.close();
        }
        
        System.out.println("✅ InstanceRegistry stopped for instance: " + instanceId);
    }
}
