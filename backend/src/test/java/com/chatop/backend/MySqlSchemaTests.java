package com.chatop.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Vérifie le vrai SQL sur une instance MySQL de test explicitement configurée. */
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
class MySqlSchemaTests {
    @ParameterizedTest
    @ValueSource(strings = {"chatop", "custom"})
    void schemaUsesTheSelectedDatabase(String prefix) throws SQLException {
        String database = "schema_test_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(System.getenv("MYSQL_TEST_URL"),
                System.getenv("MYSQL_TEST_USER"), System.getenv("MYSQL_TEST_PASSWORD"))) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            }
            try {
                connection.setCatalog(database);
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path.of("schema.sql")));
                assertEquals(database, connection.getCatalog());
                assertEquals(3, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"));
                assertEquals(3, count(connection, "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()"));
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='USERS' AND column_name='email' AND non_unique=0"));
                assertEquals(2, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='RENTALS' AND column_name IN ('surface','price') AND numeric_precision=12 AND numeric_scale=2"));
            } finally {
                connection.setCatalog("mysql");
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP DATABASE `" + database + "`");
                }
            }
        }
    }

    private int count(Connection connection, String query) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(query)) {
            result.next();
            return result.getInt(1);
        }
    }
}
