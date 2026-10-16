package com.chatop.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.chatop.backend.dto.RentalWriteRequest;
import com.chatop.backend.entity.Message;
import com.chatop.backend.entity.Rental;
import com.chatop.backend.entity.User;
import com.chatop.backend.repository.MessageRepository;
import com.chatop.backend.repository.RentalRepository;
import com.chatop.backend.repository.UserRepository;
import com.chatop.backend.service.ImageStorageService;
import com.chatop.backend.service.RentalService;
import com.chatop.backend.service.UserService;

import jakarta.persistence.Column;
import jakarta.persistence.Table;

/** Vérifie JPA et les transactions sur le vrai schéma dans un MySQL jetable. */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:schema.sql"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MySqlJpaIT.MySqlConfiguration.class, RentalService.class, UserService.class, ImageStorageService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MySqlJpaIT {
    private static final Path UPLOADS = temporaryImages();

    @Autowired
    UserRepository users;
    @Autowired
    RentalRepository rentals;
    @Autowired
    MessageRepository messages;
    @Autowired
    RentalService rentalService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlConfiguration {
        @Bean
        @ServiceConnection
        MySQLContainer mysql() {
            return new MySQLContainer("mysql:8.4")
                    .withDatabaseName("chatop_jpa_test")
                    .withPassword(UUID.randomUUID().toString());
        }
    }

    @DynamicPropertySource
    static void imageStorage(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", UPLOADS::toString);
    }

    @BeforeEach
    void clearIsolatedDatabase() throws IOException {
        transactions = new TransactionTemplate(transactionManager);
        jdbc.update("DELETE FROM MESSAGES");
        jdbc.update("DELETE FROM RENTALS");
        jdbc.update("DELETE FROM USERS");
        try (var files = Files.list(UPLOADS)) {
            for (Path file : files.toList()) Files.delete(file);
        }
    }

    @AfterAll
    static void removeTemporaryImages() throws IOException {
        try (var files = Files.walk(UPLOADS)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }

    @Test
    void schemaUsesTheSelectedDatabaseAndMatchesTheJpaConstraints() {
        assertEquals("chatop_jpa_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals(List.of("MESSAGES", "RENTALS", "USERS"), jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name",
                String.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='USERS' AND column_name='email' AND non_unique=0",
                Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='RENTALS' AND column_name IN ('surface','price') AND numeric_precision=12 AND numeric_scale=2",
                Integer.class));
        for (Class<?> entity : List.of(User.class, Rental.class, Message.class)) {
            String table = entity.getAnnotation(Table.class).name();
            for (var field : entity.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column == null || column.nullable()) continue;
                String name = column.name().isEmpty() ? field.getName() : column.name();
                assertEquals("NO", jdbc.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",
                        String.class, table, name), table + "." + name);
            }
        }
    }

    @Test
    void committedEntitiesAndServiceUpdatesAreReloadedFromMySql() throws IOException {
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8.4."));
        User owner = saveUser("owner@example.test");
        rentalService.create(request("House"), picture(), owner.getId().toString());
        Rental created = rentals.findAll().getFirst();
        rentalService.update(created.getId(), request("Housing"), owner.getId().toString());
        Message sent = messages.saveAndFlush(new Message(created.getId(), owner.getId(), "Is it available?"));

        Rental stored = rentals.findById(created.getId()).orElseThrow();
        Message storedMessage = messages.findById(sent.getId()).orElseThrow();
        User storedOwner = users.findById(owner.getId()).orElseThrow();
        assertEquals("owner@example.test", storedOwner.getEmail());
        assertNotNull(storedOwner.getCreatedAt());
        assertNotNull(storedOwner.getUpdatedAt());
        assertEquals("Housing", stored.getName());
        assertEquals(new BigDecimal("75.25"), stored.getSurface());
        assertEquals(new BigDecimal("1234.56"), stored.getPrice());
        assertEquals(owner.getId(), stored.getOwnerId());
        assertNotNull(stored.getCreatedAt());
        assertFalse(stored.getUpdatedAt().isBefore(stored.getCreatedAt()));
        assertTrue(Files.isRegularFile(UPLOADS.resolve(stored.getPicture().substring("/images/".length()))));
        assertEquals(stored.getId(), storedMessage.getRentalId());
        assertEquals(owner.getId(), storedMessage.getUserId());
        assertEquals("Is it available?", storedMessage.getMessage());
        assertNotNull(storedMessage.getCreatedAt());
    }

    @Test
    void emailUniquenessUsesTheMySqlCaseInsensitiveCollation() {
        saveUser("Owner@example.test");

        assertMySqlConstraint(1062, () -> saveUser("owner@example.test"));

        assertEquals(1, users.count());
        assertTrue(users.findByEmail("OWNER@example.test").isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rental_owner", "message_author", "message_rental"})
    void allForeignKeysRejectMissingReferences(String reference) {
        User owner = saveUser("owner@example.test");
        Rental rental = rentals.saveAndFlush(rental(owner.getId()));

        assertMySqlConstraint(1452, () -> transactions.executeWithoutResult(status -> {
            switch (reference) {
                case "rental_owner" -> rentals.saveAndFlush(rental(Integer.MAX_VALUE));
                case "message_author" -> messages.saveAndFlush(new Message(rental.getId(), Integer.MAX_VALUE, "Hello"));
                case "message_rental" -> messages.saveAndFlush(new Message(Integer.MAX_VALUE, owner.getId(), "Hello"));
                default -> throw new IllegalArgumentException(reference);
            }
        }));

        assertEquals(1, rentals.count());
        assertEquals(0, messages.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "rental"})
    void referencedRowsCannotBeDeleted(String reference) {
        User owner = saveUser("owner@example.test");
        Rental rental = rentals.saveAndFlush(rental(owner.getId()));
        messages.saveAndFlush(new Message(rental.getId(), owner.getId(), "Hello"));

        assertMySqlConstraint(1451, () -> transactions.executeWithoutResult(status -> {
            if (reference.equals("user")) {
                users.deleteById(owner.getId());
                users.flush();
            } else {
                rentals.deleteById(rental.getId());
                rentals.flush();
            }
        }));

        assertEquals(1, users.count());
        assertEquals(1, rentals.count());
        assertEquals(1, messages.count());
    }

    @Test
    void anExceptionRollsBackAllThreeFlushedEntities() {
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            User owner = saveUser("owner@example.test");
            Rental rental = rentals.saveAndFlush(rental(owner.getId()));
            messages.saveAndFlush(new Message(rental.getId(), owner.getId(), "Hello"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM MESSAGES", Integer.class));
            throw new IllegalStateException("Abort the test transaction");
        }));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM USERS", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM RENTALS", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM MESSAGES", Integer.class));
    }

    @Test
    void failedRentalCreationRollsBackTheRowAndRemovesTheImage() throws IOException {
        User owner = saveUser("owner@example.test");

        assertThrows(DataIntegrityViolationException.class,
                () -> rentalService.create(request("x".repeat(256)), picture(), owner.getId().toString()));

        assertEquals(0, rentals.count());
        assertEquals(1, users.count());
        try (var files = Files.list(UPLOADS)) {
            assertEquals(0, files.count());
        }
    }

    private User saveUser(String email) {
        return users.saveAndFlush(new User(email, "Test user", "test-only-password-hash"));
    }

    private Rental rental(Integer ownerId) {
        return new Rental("House", new BigDecimal("75.25"), new BigDecimal("1234.56"),
                "/images/test.png", "Description", ownerId);
    }

    private RentalWriteRequest request(String name) {
        return new RentalWriteRequest(name, new BigDecimal("75.25"), new BigDecimal("1234.56"), "Description");
    }

    private void assertMySqlConstraint(int errorCode, Runnable action) {
        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class, action::run);
        SQLException sql = assertInstanceOf(SQLException.class, exception.getMostSpecificCause());
        assertEquals("23000", sql.getSQLState());
        assertEquals(errorCode, sql.getErrorCode());
    }

    private MockMultipartFile picture() throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("picture", "house.png", "image/png", output.toByteArray());
    }

    private static Path temporaryImages() {
        try {
            return Files.createTempDirectory("chatop-mysql-images-test-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
