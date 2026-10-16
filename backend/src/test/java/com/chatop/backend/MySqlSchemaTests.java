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

import com.chatop.backend.entity.Message;
import com.chatop.backend.entity.Rental;
import com.chatop.backend.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Table;

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
                assertRequiredMappings(connection);
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

    private void assertRequiredMappings(Connection connection) throws SQLException {
        for (Class<?> entity : new Class<?>[] {User.class, Rental.class, Message.class}) {
            String table = entity.getAnnotation(Table.class).name();
            for (var field : entity.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column == null || column.nullable()) continue;
                String name = column.name().isEmpty() ? field.getName() : column.name();
                try (var statement = connection.prepareStatement("SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?")) {
                    statement.setString(1, table);
                    statement.setString(2, name);
                    try (var result = statement.executeQuery()) {
                        result.next();
                        assertEquals("NO", result.getString(1), table + "." + name);
                    }
                }
            }
        }
    }
}
