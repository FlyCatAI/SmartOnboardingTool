package com.flycat.rm.auth;

import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;
import com.flycat.rm.common.rbac.SecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Test/local principal bridge for docker-compose and local smoke tests.
 *
 * <p>It is disabled unless {@code auth.test-principal.enabled=true}. Protected
 * annual-summary requests without a bound principal fail closed with 401.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TestPrincipalFilter extends OncePerRequestFilter {

    private static final String ANNUAL_SUMMARY_PATH = "/api/v1/performance/annual-summary";
    private static final String EMPLOYEE_ID_HEADER = "X-Test-Employee-Id";
    private static final String EMPLOYEE_NAME_HEADER = "X-Test-Employee-Name";
    private static final String BRANCH_ID_HEADER = "X-Test-Branch-Id";
    private static final String TEAM_ID_HEADER = "X-Test-Team-Id";
    private static final String ROLE_HEADER = "X-Test-Role";

    private final boolean testPrincipalEnabled;

    public TestPrincipalFilter(
            @Value("${auth.test-principal.enabled:false}") boolean testPrincipalEnabled) {
        this.testPrincipalEnabled = testPrincipalEnabled;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        boolean createdContext = false;
        try {
            if (isProtectedAnnualSummaryPath(request) && SecurityContext.current().isEmpty()) {
                if (!testPrincipalEnabled) {
                    writeUnauthenticated(response);
                    return;
                }

                String employeeId = trimToNull(request.getHeader(EMPLOYEE_ID_HEADER));
                if (employeeId == null) {
                    writeUnauthenticated(response);
                    return;
                }

                Role role = parseRole(request.getHeader(ROLE_HEADER));
                if (role == null) {
                    writeUnauthenticated(response);
                    return;
                }

                SecurityContext.set(new Principal(
                        employeeId,
                        valueOrDefault(request.getHeader(EMPLOYEE_NAME_HEADER), employeeId),
                        valueOrDefault(request.getHeader(BRANCH_ID_HEADER), "B001"),
                        valueOrDefault(request.getHeader(TEAM_ID_HEADER), "T001"),
                        role));
                createdContext = true;
            }

            filterChain.doFilter(request, response);
        } finally {
            if (createdContext) {
                SecurityContext.clear();
            }
        }
    }

    private static boolean isProtectedAnnualSummaryPath(HttpServletRequest request) {
        return ANNUAL_SUMMARY_PATH.equals(request.getRequestURI());
    }

    private static void writeUnauthenticated(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + ErrorCode.UNAUTHENTICATED.code()
                + "\",\"slug\":\"" + ErrorCode.UNAUTHENTICATED.slug()
                + "\",\"message\":\"missing authenticated principal\"}");
    }

    private static Role parseRole(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return Role.RELATIONSHIP_MANAGER;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
