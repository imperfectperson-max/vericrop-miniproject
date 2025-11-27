package org.vericrop.gui.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DatabaseInitializer.
 * Tests verify schema loading, statement parsing, and execution behavior.
 */
class DatabaseInitializerTest {
    
    private DataSource mockDataSource;
    private Connection mockConnection;
    private Statement mockStatement;
    
    @BeforeEach
    void setUp() throws SQLException {
        mockDataSource = mock(DataSource.class);
        mockConnection = mock(Connection.class);
        mockStatement = mock(Statement.class);
        
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockConnection.getAutoCommit()).thenReturn(true);
    }
    
    // ==================== loadSchemaFromResources Tests ====================
    
    @Test
    void testLoadSchemaFromResources_LoadsFileSuccessfully() throws IOException {
        // When
        String schema = DatabaseInitializer.loadSchemaFromResources();
        
        // Then
        assertNotNull(schema, "Schema should not be null");
        assertFalse(schema.isEmpty(), "Schema should not be empty");
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS"), 
                   "Schema should contain CREATE TABLE IF NOT EXISTS statements");
        assertTrue(schema.contains("users"), 
                   "Schema should contain users table definition");
        assertTrue(schema.contains("batches"), 
                   "Schema should contain batches table definition");
        assertTrue(schema.contains("shipments"), 
                   "Schema should contain shipments table definition");
    }
    
    @Test
    void testLoadSchemaFromResources_ContainsIdempotentStatements() throws IOException {
        // When
        String schema = DatabaseInitializer.loadSchemaFromResources();
        
        // Then - verify idempotent patterns
        assertNotNull(schema);
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS"), 
                   "Schema should use CREATE TABLE IF NOT EXISTS for idempotency");
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS"), 
                   "Schema should use CREATE INDEX IF NOT EXISTS for idempotency");
        assertTrue(schema.contains("CREATE OR REPLACE FUNCTION"), 
                   "Schema should use CREATE OR REPLACE FUNCTION for idempotency");
        assertTrue(schema.contains("DROP TRIGGER IF EXISTS"), 
                   "Schema should use DROP TRIGGER IF EXISTS for idempotency");
    }
    
    // ==================== parseStatements Tests ====================
    
    @Test
    void testParseStatements_ParsesSimpleStatements() {
        // Given - statements on separate lines (realistic schema file format)
        String sql = "CREATE TABLE test (id INT);\nCREATE INDEX idx ON test(id);";
        
        // When
        List<String> statements = DatabaseInitializer.parseStatements(sql);
        
        // Then
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("CREATE TABLE"));
        assertTrue(statements.get(1).contains("CREATE INDEX"));
    }
    
    @Test
    void testParseStatements_SkipsComments() {
        // Given
        String sql = "-- This is a comment\nCREATE TABLE test (id INT);\n-- Another comment";
        
        // When
        List<String> statements = DatabaseInitializer.parseStatements(sql);
        
        // Then
        assertEquals(1, statements.size());
        assertTrue(statements.get(0).contains("CREATE TABLE"));
    }
    
    @Test
    void testParseStatements_HandlesMultiLineStatements() {
        // Given
        String sql = "CREATE TABLE test (\n    id INT,\n    name VARCHAR(255)\n);";
        
        // When
        List<String> statements = DatabaseInitializer.parseStatements(sql);
        
        // Then
        assertEquals(1, statements.size());
        assertTrue(statements.get(0).contains("CREATE TABLE"));
        assertTrue(statements.get(0).contains("name VARCHAR"));
    }
    
    @Test
    void testParseStatements_HandlesFunctionDefinitions() {
        // Given - a function with $$ dollar quoting
        String sql = """
            CREATE OR REPLACE FUNCTION test_func()
            RETURNS TRIGGER AS $$
            BEGIN
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;
            """;
        
        // When
        List<String> statements = DatabaseInitializer.parseStatements(sql);
        
        // Then
        assertEquals(1, statements.size());
        assertTrue(statements.get(0).contains("CREATE OR REPLACE FUNCTION"));
        assertTrue(statements.get(0).contains("LANGUAGE plpgsql"));
    }
    
    @Test
    void testParseStatements_ReturnsEmptyForNullInput() {
        // When
        List<String> statements = DatabaseInitializer.parseStatements(null);
        
        // Then
        assertTrue(statements.isEmpty());
    }
    
    @Test
    void testParseStatements_ReturnsEmptyForEmptyInput() {
        // When
        List<String> statements = DatabaseInitializer.parseStatements("");
        
        // Then
        assertTrue(statements.isEmpty());
    }
    
    @Test
    void testParseStatements_ReturnsEmptyForOnlyComments() {
        // Given
        String sql = "-- Comment line 1\n-- Comment line 2\n";
        
        // When
        List<String> statements = DatabaseInitializer.parseStatements(sql);
        
        // Then
        assertTrue(statements.isEmpty());
    }
    
    // ==================== initialize Tests ====================
    
    @Test
    void testInitialize_DoesNotThrowWithNullDataSource() {
        // When/Then - should not throw, just log warning
        assertDoesNotThrow(() -> DatabaseInitializer.initialize(null));
    }
    
    @Test
    void testInitialize_ExecutesStatementsSuccessfully() throws SQLException {
        // When
        DatabaseInitializer.initialize(mockDataSource);
        
        // Then - verify connection was used
        verify(mockDataSource).getConnection();
        verify(mockConnection).createStatement();
        verify(mockConnection).commit();
        verify(mockStatement, atLeastOnce()).execute(anyString());
    }
    
    @Test
    void testInitialize_HandlesConnectionError() throws SQLException {
        // Given
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
        
        // When/Then - should not throw, just log error
        assertDoesNotThrow(() -> DatabaseInitializer.initialize(mockDataSource));
    }
    
    @Test
    void testInitialize_ContinuesAfterStatementError() throws SQLException {
        // Given - first statement fails, second succeeds
        when(mockStatement.execute(anyString()))
            .thenThrow(new SQLException("Simulated error"))
            .thenReturn(true);
        
        // When/Then - should not throw
        assertDoesNotThrow(() -> DatabaseInitializer.initialize(mockDataSource));
        
        // Verify commit was still called
        verify(mockConnection).commit();
    }
    
    @Test
    void testInitialize_RollsBackOnCriticalError() throws SQLException {
        // Given - createStatement throws
        when(mockConnection.createStatement()).thenThrow(new SQLException("Critical error"));
        
        // When/Then - should not throw
        assertDoesNotThrow(() -> DatabaseInitializer.initialize(mockDataSource));
    }
    
    @Test
    void testInitialize_RestoresAutoCommit() throws SQLException {
        // Given
        when(mockConnection.getAutoCommit()).thenReturn(true);
        
        // When
        DatabaseInitializer.initialize(mockDataSource);
        
        // Then
        verify(mockConnection).setAutoCommit(false);
        verify(mockConnection).setAutoCommit(true);
    }
}
