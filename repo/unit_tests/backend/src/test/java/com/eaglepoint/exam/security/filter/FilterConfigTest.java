package com.eaglepoint.exam.security.filter;

import com.eaglepoint.exam.security.repository.NonceReplayRepository;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class FilterConfigTest {

    @Test
    void testFilterBeansAndRegistrationOrder() {
        FilterConfig config = new FilterConfig();
        ObjectMapper mapper = new ObjectMapper();

        SecurityFilter securityFilter = config.securityFilter(
                mock(SessionRepository.class), mock(UserRepository.class), mapper);
        RequestSigningFilter signingFilter = config.requestSigningFilter(
                mock(SessionRepository.class), mock(NonceReplayRepository.class), mapper);
        RateLimitFilter rateLimitFilter = config.rateLimitFilter(mapper, 60, 300);

        assertNotNull(securityFilter);
        assertNotNull(signingFilter);
        assertNotNull(rateLimitFilter);

        FilterRegistrationBean<SecurityFilter> securityReg = config.securityFilterRegistration(securityFilter);
        FilterRegistrationBean<RequestSigningFilter> signingReg = config.requestSigningFilterRegistration(signingFilter);
        FilterRegistrationBean<RateLimitFilter> rateReg = config.rateLimitFilterRegistration(rateLimitFilter);

        assertEquals(1, securityReg.getOrder());
        assertEquals(2, signingReg.getOrder());
        assertEquals(3, rateReg.getOrder());
        assertEquals("securityFilter", securityReg.getFilterName());
        assertEquals("requestSigningFilter", signingReg.getFilterName());
        assertEquals("rateLimitFilter", rateReg.getFilterName());
    }
}
