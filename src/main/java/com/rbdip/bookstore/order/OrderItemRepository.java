package com.rbdip.bookstore.order;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = "product")
    List<OrderItem> findByOrderIdIn(Collection<Long> orderIds);

    boolean existsByProductId(Long productId);
}
