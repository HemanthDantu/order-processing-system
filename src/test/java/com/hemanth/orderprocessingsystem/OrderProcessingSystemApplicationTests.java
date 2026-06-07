package com.hemanth.orderprocessingsystem;

import com.hemanth.orderprocessingsystem.history.OrderStatusHistoryRepository;
import com.hemanth.orderprocessingsystem.order.OrderRepository;
import com.hemanth.orderprocessingsystem.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

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

    @Test
    void contextLoads() {
    }

}
