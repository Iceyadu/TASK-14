package com.eaglepoint.exam.security.filter;

import com.eaglepoint.exam.security.repository.NonceReplayRepository;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the three security filters in the correct order and maps
 * them to the {@code /api/*} URL pattern.
 */
@Configuration
public class FilterConfig {

    @Bean
    public SecurityFilter securityFilter(SessionRepository sessionRepository,
                                         UserRepository userRepository,
                                         ObjectMapper objectMapper) {
        return new SecurityFilter(sessionRepository, userRepository, objectMapper);
    }

    @Bean
    public RequestSigningFilter requestSigningFilter(SessionRepository sessionRepository,
                                                     NonceReplayRepository nonceReplayRepository,
                                                     ObjectMapper objectMapper) {
        return new RequestSigningFilter(sessionRepository, nonceReplayRepository, objectMapper);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.user-per-minute:60}") int userLimit,
            @Value("${app.rate-limit.ip-per-minute:300}") int ipLimit) {
        return new RateLimitFilter(objectMapper, userLimit, ipLimit);
    }

    @Bean
    public FilterRegistrationBean<SecurityFilter> securityFilterRegistration(SecurityFilter filter) {
        FilterRegistrationBean<SecurityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        registration.setName("securityFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestSigningFilter> requestSigningFilterRegistration(RequestSigningFilter filter) {
        FilterRegistrationBean<RequestSigningFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        registration.setName("requestSigningFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(3);
        registration.setName("rateLimitFilter");
        return registration;
    }
}
