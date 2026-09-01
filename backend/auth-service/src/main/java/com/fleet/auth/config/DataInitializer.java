package com.fleet.auth.config;

import com.fleet.auth.entity.User;
import com.fleet.auth.model.UserRole;
import com.fleet.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initAdminUser(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JdbcTemplate jdbcTemplate
    ) {
        return args -> {
            try {
                // Drop outdated PostgreSQL check constraint created when ADMIN role did not exist
                jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
            } catch (Exception e) {
                log.warn("Could not drop users_role_check constraint: {}", e.getMessage());
            }

            String adminEmail = "admin@fleet.com";
            if (!userRepository.existsByEmail(adminEmail)) {
                User admin = User.builder()
                    .fullName("System Admin")
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .role(UserRole.ADMIN)
                    .matricule("ADM-001")
                    .build();

                userRepository.save(admin);
                log.info("Default ADMIN user initialized: email={}, password=admin123", adminEmail);
            }
        };
    }
}
