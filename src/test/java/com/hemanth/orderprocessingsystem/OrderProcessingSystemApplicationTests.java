package com.hemanth.orderprocessingsystem;

import com.hemanth.orderprocessingsystem.history.OrderStatusHistoryRepository;
import com.hemanth.orderprocessingsystem.idempotency.IdempotencyRepository;
import com.hemanth.orderprocessingsystem.order.OrderRepository;
import com.hemanth.orderprocessingsystem.processing.OrderProcessingRepository;
import com.hemanth.orderprocessingsystem.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
class OrderProcessingSystemApplicationTests {

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @MockBean
    private IdempotencyRepository idempotencyRepository;

    @MockBean
    private PlatformTransactionManager platformTransactionManager;

    @MockBean
    private OrderProcessingRepository orderProcessingRepository;

    @Test
    void contextLoads() {
    }

}
