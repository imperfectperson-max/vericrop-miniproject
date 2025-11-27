package org.vericrop.gui.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to initialize the database schema at application startup.
 * <p>
 * This class loads and executes the idempotent schema.sql file from resources,
 * ensuring all required tables exist before the application attempts to use them.
 * The schema uses CREATE TABLE IF NOT EXISTS for safety when re-running.
 * <p>
 * Usage: Call {@link #initialize(DataSource)} after creating the DataSource
 * but before any DAO or repository attempts to access the database.
 */
public class DatabaseInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    
    /**
     * Path to the schema SQL file in resources.
     */
    private static final String SCHEMA_PATH = "/db/schema.sql";
    
    /**
     * Maximum length for SQL statement previews in log messages.
     */
    private static final int MAX_LOG_PREVIEW_LENGTH = 80;
    
    /**
     * Truncation length for SQL statement previews (leaves room for ellipsis).
     */
    private static final int PREVIEW_TRUNCATION_LENGTH = 77;
    
    /**
     * Private constructor to prevent instantiation.
     */
    private DatabaseInitializer() {
        // Utility class - no instantiation
    }
    
    /**
     * Initialize the database schema by executing the schema.sql file.
     * <p>
     * This method is resilient - it logs errors but does not throw exceptions
     * to avoid crashing the UI if the database is temporarily unavailable.
     * The schema.sql file uses idempotent statements (CREATE TABLE IF NOT EXISTS)
     * so it is safe to run multiple times.
     *
     * @param dataSource The DataSource to use for database connections
     */
    public static void initialize(DataSource dataSource) {
        if (dataSource == null) {
            logger.warn("DatabaseInitializer: DataSource is null, skipping schema initialization");
            return;
        }
        
        logger.info("DatabaseInitializer: Starting schema initialization...");
        
        try {
            String schemaSql = loadSchemaFromResources();
            if (schemaSql == null || schemaSql.trim().isEmpty()) {
                logger.warn("DatabaseInitializer: Schema file is empty or not found, skipping initialization");
                return;
            }
            
            List<String> statements = parseStatements(schemaSql);
            executeStatements(dataSource, statements);
            
            logger.info("DatabaseInitializer: Schema initialization completed successfully");
        } catch (IOException e) {
            logger.error("DatabaseInitializer: Failed to load schema file: {}", e.getMessage());
        } catch (SQLException e) {
            logger.error("DatabaseInitializer: Database error during schema initialization: {} (SQLState: {})", 
                        e.getMessage(), e.getSQLState());
        } catch (Exception e) {
            logger.error("DatabaseInitializer: Unexpected error during schema initialization", e);
        }
    }
    
    /**
     * Load the schema SQL file from resources.
     *
     * @return The contents of the schema file as a String
     * @throws IOException If the file cannot be read
     */
    static String loadSchemaFromResources() throws IOException {
        try (InputStream inputStream = DatabaseInitializer.class.getResourceAsStream(SCHEMA_PATH)) {
            if (inputStream == null) {
                logger.warn("DatabaseInitializer: Schema file not found at {}", SCHEMA_PATH);
                return null;
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                return content.toString();
            }
        }
    }
    
    /**
     * Parse the SQL content into individual statements.
     * Splits by semicolon while handling multi-line statements and comments.
     *
     * @param sql The SQL content to parse
     * @return List of individual SQL statements
     */
    static List<String> parseStatements(String sql) {
        List<String> statements = new ArrayList<>();
        
        if (sql == null || sql.trim().isEmpty()) {
            return statements;
        }
        
        // Track if we're inside a function/trigger definition
        StringBuilder currentStatement = new StringBuilder();
        boolean inFunctionBody = false;
        int dollarQuoteDepth = 0;
        
        String[] lines = sql.split("\n");
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // Skip empty lines and single-line comments
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("--")) {
                continue;
            }
            
            // Track $$ dollar quoting for function bodies
            int dollarQuotes = countOccurrences(line, "$$");
            if (dollarQuotes > 0) {
                dollarQuoteDepth += dollarQuotes;
                // If we have an even number of $$ total, we've closed the function body
                inFunctionBody = (dollarQuoteDepth % 2) != 0;
            }
            
            currentStatement.append(line).append("\n");
            
            // Check if this line ends a statement (semicolon outside function body)
            if (trimmedLine.endsWith(";") && !inFunctionBody) {
                String statement = currentStatement.toString().trim();
                if (!statement.isEmpty() && !isOnlyComments(statement)) {
                    statements.add(statement);
                }
                currentStatement = new StringBuilder();
            }
        }
        
        // Handle any remaining statement without trailing semicolon
        String remaining = currentStatement.toString().trim();
        if (!remaining.isEmpty() && !isOnlyComments(remaining)) {
            statements.add(remaining);
        }
        
        return statements;
    }
    
    /**
     * Count occurrences of a substring in a string.
     */
    private static int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
    
    /**
     * Check if a string contains only SQL comments.
     */
    private static boolean isOnlyComments(String sql) {
        String[] lines = sql.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Execute the list of SQL statements against the database.
     *
     * @param dataSource The DataSource to use
     * @param statements The list of SQL statements to execute
     * @throws SQLException If a database error occurs
     */
    private static void executeStatements(DataSource dataSource, List<String> statements) throws SQLException {
        int successCount = 0;
        int failCount = 0;
        
        try (Connection conn = dataSource.getConnection()) {
            // Disable auto-commit for batch execution
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    try {
                        stmt.execute(sql);
                        successCount++;
                        logger.debug("DatabaseInitializer: Executed statement successfully");
                    } catch (SQLException e) {
                        failCount++;
                        // Log the error but continue with other statements
                        // Many errors are expected (e.g., "relation already exists" warnings)
                        String preview = sql.length() > MAX_LOG_PREVIEW_LENGTH 
                            ? sql.substring(0, PREVIEW_TRUNCATION_LENGTH) + "..." 
                            : sql;
                        preview = preview.replaceAll("\\s+", " ");
                        logger.debug("DatabaseInitializer: Statement warning/error: {} for statement: {}", 
                                    e.getMessage(), preview);
                    }
                }
                
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
        
        logger.info("DatabaseInitializer: Executed {} statements successfully, {} with warnings/errors", 
                    successCount, failCount);
    }
}
