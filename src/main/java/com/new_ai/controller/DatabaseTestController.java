package com.new_ai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/db-test")
public class DatabaseTestController {
    
    @Autowired
    private DataSource dataSource;
    
    @GetMapping("/connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("=== Database Connection Test (Spring Boot) ===");
            
            // Get connection from DataSource
            Connection connection = dataSource.getConnection();
            System.out.println("[OK] Got connection from DataSource");
            result.put("dataSourceConnection", "SUCCESS");
            
            // Test basic query
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT version()");
            
            if (resultSet.next()) {
                String version = resultSet.getString(1);
                System.out.println("[OK] Query executed: " + version.substring(0, Math.min(50, version.length())) + "...");
                result.put("queryTest", "SUCCESS");
                result.put("postgresVersion", version.substring(0, Math.min(100, version.length())));
            }
            
            // Test vector extension
            ResultSet extResult = statement.executeQuery("SELECT * FROM pg_extension WHERE extname = 'vector'");
            if (extResult.next()) {
                System.out.println("[OK] pgvector extension found");
                result.put("vectorExtension", "INSTALLED");
            } else {
                System.out.println("[WARN] pgvector extension not found");
                result.put("vectorExtension", "NOT_FOUND");
            }
            
            // Test table operations
            try {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS connection_test (id SERIAL PRIMARY KEY, test_time TIMESTAMP DEFAULT NOW())");
                statement.executeUpdate("INSERT INTO connection_test DEFAULT VALUES");
                
                ResultSet testResult = statement.executeQuery("SELECT COUNT(*) FROM connection_test");
                if (testResult.next()) {
                    int count = testResult.getInt(1);
                    System.out.println("[OK] Test table operations successful, rows: " + count);
                    result.put("tableOperations", "SUCCESS");
                    result.put("testTableRows", count);
                }
                
                statement.executeUpdate("DROP TABLE connection_test");
                System.out.println("[OK] Test table cleaned up");
                
            } catch (Exception e) {
                System.out.println("[ERROR] Table operations failed: " + e.getMessage());
                result.put("tableOperations", "FAILED: " + e.getMessage());
            }
            
            // Close connections
            resultSet.close();
            statement.close();
            connection.close();
            System.out.println("[OK] Connection closed properly");
            result.put("connectionClosed", "SUCCESS");
            
            result.put("status", "SUCCESS");
            result.put("message", "Database connection test completed");
            
        } catch (Exception e) {
            System.err.println("[ERROR] Database test failed: " + e.getMessage());
            e.printStackTrace();
            
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
}