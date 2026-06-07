package com.hemanth.orderprocessingsystem;

import com.hemanth.orderprocessingsystem.history.OrderStatusHistoryRepository;
import com.hemanth.orderprocessingsystem.idempotency.IdempotencyRepository;
import com.hemanth.orderprocessingsystem.order.OrderRepository;
import com.hemanth.orderprocessingsystem.processing.OrderProcessingRepository;
import com.hemanth.orderprocessingsystem.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
class OrderProcessingSystemApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @MockitoBean
    private IdempotencyRepository idempotencyRepository;

    @MockitoBean
    private PlatformTransactionManager platformTransactionManager;

    @MockitoBean
    private OrderProcessingRepository orderProcessingRepository;

    @Test
    void contextLoads() {
    }

}
