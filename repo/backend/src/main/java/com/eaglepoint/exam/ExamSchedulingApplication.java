package com.eaglepoint.exam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main entry point for the Secure Exam Scheduling and Notification Management System.
 *
 * Spring Security auto-configuration is excluded because the application uses
 * its own custom session-token authentication mechanism rather than Spring
 * Security filters.  The spring-boot-starter-security dependency is retained
 * solely for the {@link org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder}
 * utility used in password hashing.
 */
@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@EnableJpaAuditing
@EnableTransactionManagement
@EnableScheduling
public class ExamSchedulingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamSchedulingApplication.class, args);
    }
}
