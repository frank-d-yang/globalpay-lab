package com.globalpay.lab.security;

import com.globalpay.lab.security.application.MerchantApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantApiKeyAuthenticationFilterTest {

    @Mock
    private MerchantApiKeyService apiKeyService;

    @Mock
    private FilterChain filterChain;

    @Test
    void shouldAuthenticateValidApiKey() throws Exception {

        String rawKey = "test-api-key";
        UUID merchantId = UUID.randomUUID();

        when(apiKeyService.authenticate(rawKey))
                .thenReturn(merchantId);

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader("X-API-Key", rawKey);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        doAnswer(invocation -> {
            Authentication auth =
                    SecurityContextHolder.getContext()
                            .getAuthentication();

            assertNotNull(auth);
            assertTrue(auth.isAuthenticated());
            assertEquals(merchantId, auth.getPrincipal());
            return null;

        }).when(filterChain).doFilter(
                any(ServletRequest.class),
                any(ServletResponse.class)
        );

        var filter = new MerchantApiKeyAuthenticationFilter(
                apiKeyService
        );

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(
                any(ServletRequest.class),
                any(ServletResponse.class)
        );

        assertNull(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        );
    }

    @Test
    void shouldRejectInvalidApiKey() throws Exception {

        String rawKey = "invalid-api-key";

        when(apiKeyService.authenticate(rawKey))
                .thenThrow(
                        new BadCredentialsException("Invalid API key")
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader("X-API-Key", rawKey);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        var filter = new MerchantApiKeyAuthenticationFilter(
                apiKeyService
        );

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldContinueWithoutApiKey() throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        var filter = new MerchantApiKeyAuthenticationFilter(
                apiKeyService
        );

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(apiKeyService);
        verify(filterChain).doFilter(
                any(ServletRequest.class),
                any(ServletResponse.class)
        );
    }
}

