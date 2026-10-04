package com.rbdip.bookstore.order;

import com.rbdip.bookstore.purchase.PurchaseLookup;
import org.springframework.stereotype.Service;

@Service
public class OrderPurchaseLookup implements PurchaseLookup {

    private final OrderItemRepository orderItemRepository;

    public OrderPurchaseLookup(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    public boolean hasPurchaseFor(Long productId) {
        return orderItemRepository.existsByProductId(productId);
    }
}
