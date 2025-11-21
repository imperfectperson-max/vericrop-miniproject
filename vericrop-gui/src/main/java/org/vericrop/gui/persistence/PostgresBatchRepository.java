package org.vericrop.gui.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vericrop.gui.config.ConfigService;
import org.vericrop.gui.models.BatchRecord;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Batch metadata persistence using PostgreSQL.
 * Uses JDBC with HikariCP connection pooling for optimal performance.
 * 
 * Provides CRUD operations for BatchRecord entities.
 */
public class PostgresBatchRepository {
    private static final Logger logger = LoggerFactory.getLogger(PostgresBatchRepository.class);
    
    private final HikariDataSource dataSource;

    /**
     * Constructor with configuration from ConfigService
     */
    public PostgresBatchRepository(ConfigService config) {
        this.dataSource = createDataSource(config);
        logger.info("✅ PostgresBatchRepository initialized with connection pool");
    }

    /**
     * Create HikariCP data source with connection pooling
     */
    private HikariDataSource createDataSource(ConfigService config) {
        HikariConfig hikariConfig = new HikariConfig();
        
        // JDBC connection settings
        hikariConfig.setJdbcUrl(config.getDbUrl());
        hikariConfig.setUsername(config.getDbUser());
        hikariConfig.setPassword(config.getDbPassword());
        
        // Connection pool settings
        hikariConfig.setMaximumPoolSize(config.getDbPoolSize());
        hikariConfig.setConnectionTimeout(config.getDbConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getDbIdleTimeout());
        hikariConfig.setMaxLifetime(config.getDbMaxLifetime());
        
        // Performance and reliability settings
        hikariConfig.setAutoCommit(true);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setPoolName("VeriCropBatchPool");
        
        // Cache prepared statements
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        logger.debug("HikariCP configuration: url={}, user={}, maxPoolSize={}", 
                config.getDbUrl(), config.getDbUser(), config.getDbPoolSize());
        
        return new HikariDataSource(hikariConfig);
    }

    /**
     * Create (insert) a new batch record
     */
    public BatchRecord create(BatchRecord batch) throws SQLException {
        String sql = "INSERT INTO batches (batch_id, name, farmer, product_type, quantity, " +
                "quality_score, quality_label, data_hash, timestamp, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::timestamp, ?, ?, ?) " +
                "RETURNING id, created_at, updated_at";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, batch.getBatchId());
            stmt.setString(2, batch.getName());
            stmt.setString(3, batch.getFarmer());
            stmt.setString(4, batch.getProductType());
            stmt.setInt(5, batch.getQuantity() != null ? batch.getQuantity() : 0);
            
            if (batch.getQualityScore() != null) {
                stmt.setDouble(6, batch.getQualityScore());
            } else {
                stmt.setNull(6, Types.DECIMAL);
            }
            
            stmt.setString(7, batch.getQualityLabel());
            stmt.setString(8, batch.getDataHash());
            stmt.setString(9, batch.getTimestamp() != null ? batch.getTimestamp() : 
                    LocalDateTime.now().toString());
            stmt.setString(10, batch.getStatus() != null ? batch.getStatus() : "created");
            
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            stmt.setTimestamp(11, now);
            stmt.setTimestamp(12, now);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    batch.setId(rs.getLong("id"));
                    batch.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    batch.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
            
            logger.info("Created batch record: batch_id={}, id={}", batch.getBatchId(), batch.getId());
            return batch;
        }
    }

    /**
     * Read (find) a batch record by batch_id
     */
    public Optional<BatchRecord> findByBatchId(String batchId) throws SQLException {
        String sql = "SELECT * FROM batches WHERE batch_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, batchId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBatch(rs));
                }
            }
        }
        
        return Optional.empty();
    }

    /**
     * Read (find) a batch record by database id
     */
    public Optional<BatchRecord> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM batches WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBatch(rs));
                }
            }
        }
        
        return Optional.empty();
    }

    /**
     * Find all batch records
     */
    public List<BatchRecord> findAll() throws SQLException {
        String sql = "SELECT * FROM batches ORDER BY created_at DESC";
        List<BatchRecord> batches = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                batches.add(mapResultSetToBatch(rs));
            }
        }
        
        return batches;
    }

    /**
     * Find batch records by farmer
     */
    public List<BatchRecord> findByFarmer(String farmer) throws SQLException {
        String sql = "SELECT * FROM batches WHERE farmer = ? ORDER BY created_at DESC";
        List<BatchRecord> batches = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, farmer);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    batches.add(mapResultSetToBatch(rs));
                }
            }
        }
        
        return batches;
    }

    /**
     * Find batch records by status
     */
    public List<BatchRecord> findByStatus(String status) throws SQLException {
        String sql = "SELECT * FROM batches WHERE status = ? ORDER BY created_at DESC";
        List<BatchRecord> batches = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    batches.add(mapResultSetToBatch(rs));
                }
            }
        }
        
        return batches;
    }

    /**
     * Update a batch record
     */
    public BatchRecord update(BatchRecord batch) throws SQLException {
        String sql = "UPDATE batches SET name = ?, farmer = ?, product_type = ?, quantity = ?, " +
                "quality_score = ?, quality_label = ?, data_hash = ?, status = ?, updated_at = ? " +
                "WHERE batch_id = ? " +
                "RETURNING id, created_at, updated_at";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, batch.getName());
            stmt.setString(2, batch.getFarmer());
            stmt.setString(3, batch.getProductType());
            stmt.setInt(4, batch.getQuantity() != null ? batch.getQuantity() : 0);
            
            if (batch.getQualityScore() != null) {
                stmt.setDouble(5, batch.getQualityScore());
            } else {
                stmt.setNull(5, Types.DECIMAL);
            }
            
            stmt.setString(6, batch.getQualityLabel());
            stmt.setString(7, batch.getDataHash());
            stmt.setString(8, batch.getStatus());
            stmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(10, batch.getBatchId());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    batch.setId(rs.getLong("id"));
                    batch.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    batch.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
            
            logger.info("Updated batch record: batch_id={}", batch.getBatchId());
            return batch;
        }
    }

    /**
     * Delete a batch record by batch_id
     */
    public boolean delete(String batchId) throws SQLException {
        String sql = "DELETE FROM batches WHERE batch_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, batchId);
            int rowsDeleted = stmt.executeUpdate();
            
            if (rowsDeleted > 0) {
                logger.info("Deleted batch record: batch_id={}", batchId);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Count total number of batches
     */
    public long count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM batches";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        
        return 0;
    }

    /**
     * Test database connection
     */
    public boolean testConnection() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);  // 5 second timeout
        } catch (SQLException e) {
            logger.error("Database connection test failed", e);
            return false;
        }
    }

    /**
     * Map ResultSet row to BatchRecord object
     */
    private BatchRecord mapResultSetToBatch(ResultSet rs) throws SQLException {
        BatchRecord batch = new BatchRecord();
        
        batch.setId(rs.getLong("id"));
        batch.setBatchId(rs.getString("batch_id"));
        batch.setName(rs.getString("name"));
        batch.setFarmer(rs.getString("farmer"));
        batch.setProductType(rs.getString("product_type"));
        batch.setQuantity(rs.getInt("quantity"));
        
        double qualityScore = rs.getDouble("quality_score");
        if (!rs.wasNull()) {
            batch.setQualityScore(qualityScore);
        }
        
        batch.setQualityLabel(rs.getString("quality_label"));
        batch.setDataHash(rs.getString("data_hash"));
        batch.setTimestamp(rs.getString("timestamp"));
        batch.setStatus(rs.getString("status"));
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            batch.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            batch.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        
        return batch;
    }

    /**
     * Get the underlying DataSource for use by other DAOs
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Close data source and release all connections
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("PostgresBatchRepository shutdown complete");
        }
    }
}
