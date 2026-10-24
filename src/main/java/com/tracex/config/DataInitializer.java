package com.tracex.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.tracex.model.jpa.AppUser;
import com.tracex.repository.AppUserRepository;
import com.tracex.repository.Neo4jGateway;

/** Runs once at start-up: creates demo users and the Neo4j uniqueness constraint. */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DataInitializer.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Neo4jGateway neo4j;

    public DataInitializer(AppUserRepository users, PasswordEncoder encoder, Neo4jGateway neo4j) {
        this.users = users;
        this.encoder = encoder;
        this.neo4j = neo4j;
    }

    @Override
    public void run(String... args) {
        if (users.count() == 0) {
            users.save(new AppUser("analyst", encoder.encode("analyst123"), "ANALYST"));
            users.save(new AppUser("supervisor", encoder.encode("super123"), "SUPERVISOR"));
            users.save(new AppUser("auditor", encoder.encode("audit123"), "AUDITOR"));
            LOG.info("Created demo users: analyst, supervisor, auditor");
        }
        try {
            neo4j.write("CREATE CONSTRAINT entity_key IF NOT EXISTS FOR (e:Entity) REQUIRE e.key IS UNIQUE",
                    Map.of());
        } catch (Exception ex) {
            LOG.warn("Could not reach Neo4j at start-up. Check that it is running and that the password in "
                    + "application.properties is correct. Details: {}", ex.getMessage());
        }
    }
}
