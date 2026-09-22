package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * God-класс: валидация, расчёт цены, персистентность и "уведомление
 * клиента" смешаны в одном методе. Цель для рефакторинга по SRP в ЛР1.
 */
@Service
public class OrderService {

    private static final String DEFAULT_CUSTOMER_TYPE = "regular";
    private static final int DEFAULT_QUANTITY = 1;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEmailService emailService;
    private final PricingCalculator pricingCalculator = new PricingCalculator();

    public OrderService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderEmailService emailService) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.emailService = emailService;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        validate(request);
        List<Product> products = loadProducts(request);
        BigDecimal total = calculateTotal(request, products);
        Order order = saveOrder(request);
        saveOrderItems(order, products, request);
        emailService.sendConfirmation(request.customerFullName(), order.getId(), total);
        return order;
    }

    private void validate(CreateOrderRequest request) {
        requireText(request.customerFullName(), "customerFullName");
        requireText(request.customerAddress(), "customerAddress");
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private List<Product> loadProducts(CreateOrderRequest request) {
        List<Product> products = new ArrayList<>();
        for (CreateOrderRequest.Item item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("product " + item.productId() + " not found"));
            int quantity = item.quantity() == null ? DEFAULT_QUANTITY : item.quantity();
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            products.add(product);
        }
        return products;
    }

    private BigDecimal calculateTotal(CreateOrderRequest request, List<Product> products) {
        List<PricingCalculator.LineItem> lineItems = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            int quantity = request.items().get(i).quantity() == null
                    ? DEFAULT_QUANTITY
                    : request.items().get(i).quantity();
            lineItems.add(new PricingCalculator.LineItem(products.get(i).getPrice(), quantity));
        }
        String customerType = request.customerType() == null ? DEFAULT_CUSTOMER_TYPE : request.customerType();
        return pricingCalculator.calculateOrderTotal(lineItems, customerType, request.couponCode());
    }

    private Order saveOrder(CreateOrderRequest request) {
        Order order = new Order(
                request.customerFullName(), request.customerAddress(), request.customerPhone(), "new");
        return orderRepository.save(order);
    }

    private void saveOrderItems(Order order, List<Product> products, CreateOrderRequest request) {
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            int quantity = request.items().get(i).quantity() == null
                    ? DEFAULT_QUANTITY
                    : request.items().get(i).quantity();
            orderItemRepository.save(new OrderItem(order.getId(), product.getName(), product.getPrice(), quantity));
        }
    }
}