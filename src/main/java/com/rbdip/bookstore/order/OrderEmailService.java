package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderEmailService {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailService.class);

    public void sendConfirmation(String customerName, Long orderId, BigDecimal total) {
        log.info("[email] Dear {}, your order #{} for {} has been placed.", customerName, orderId, total);
    }
}