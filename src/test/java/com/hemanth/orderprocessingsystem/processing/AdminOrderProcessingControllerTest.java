package com.hemanth.orderprocessingsystem.processing;

import com.hemanth.orderprocessingsystem.auth.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web authorization tests for the manual order-processing trigger.
 */
@WebMvcTest(AdminOrderProcessingController.class)
@Import({
        com.hemanth.orderprocessingsystem.auth.SecurityConfig.class,
        com.hemanth.orderprocessingsystem.exception.ApiErrorResponseWriter.class
})
class AdminOrderProcessingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderProcessingJobService jobService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void allowMockJwtFilterToContinueChain() throws Exception {
        doAnswer(invocation -> {
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class),
                any(FilterChain.class)
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanTriggerOrderProcessing() throws Exception {
        Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant completedAt = Instant.parse("2026-01-01T00:00:01Z");
        when(jobService.processPendingOrders())
                .thenReturn(new OrderProcessingJobResponse(3, 1000, startedAt, completedAt));

        mockMvc.perform(post("/api/v1/admin/scheduler/order-processing/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount", is(3)))
                .andExpect(jsonPath("$.durationMs", is(1000)));

        verify(jobService).processPendingOrders();
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotTriggerOrderProcessing() throws Exception {
        mockMvc.perform(post("/api/v1/admin/scheduler/order-processing/trigger"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", is("Access denied")))
                .andExpect(jsonPath("$.path", is("/api/v1/admin/scheduler/order-processing/trigger")));

        verify(jobService, never()).processPendingOrders();
    }
}
