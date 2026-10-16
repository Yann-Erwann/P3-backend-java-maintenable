package com.chatop.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.chatop.backend.dto.LoginRequest;
import com.chatop.backend.dto.RegisterRequest;

import jakarta.validation.Validation;
import jakarta.validation.constraints.Size;

class SwaggerDocumentationTests {
    @Test
    void registrationExampleAndLimitsMatchTheDto() throws Exception {
        Map<?, ?> properties = properties("RegisterRequest");
        var request = new RegisterRequest(example(properties, "email"), example(properties, "name"),
                example(properties, "password"));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
        for (String name : new String[] {"name", "email", "password"}) {
            Map<?, ?> property = (Map<?, ?>) properties.get(name);
            Size constraint = RegisterRequest.class.getDeclaredField(name).getAnnotation(Size.class);
            assertEquals(constraint.max(), property.get("maxLength"));
            if (constraint.min() > 0) assertEquals(constraint.min(), property.get("minLength"));
        }
        assertEquals("email", ((Map<?, ?>) properties.get("email")).get("format"));
    }

    @Test
    void mockLoginExampleRemainsValidForTheLoginDto() throws IOException {
        Map<?, ?> properties = properties("LoginRequest");
        var request = new LoginRequest(example(properties, "email"), example(properties, "password"));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }

    private Map<?, ?> properties(String name) throws IOException {
        try (var reader = Files.newBufferedReader(Path.of("swagger.yml"))) {
            Map<?, ?> root = new Yaml().load(reader);
            Map<?, ?> components = (Map<?, ?>) root.get("components");
            Map<?, ?> schemas = (Map<?, ?>) components.get("schemas");
            return (Map<?, ?>) ((Map<?, ?>) schemas.get(name)).get("properties");
        }
    }

    private String example(Map<?, ?> properties, String name) {
        return (String) ((Map<?, ?>) properties.get(name)).get("example");
    }
}
