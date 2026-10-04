package com.rbdip.bookstore.review;

import com.rbdip.bookstore.purchase.PurchaseLookup;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Доступ к истории покупок выполняется через отдельный контракт
 */
@Service
public class ReviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final PurchaseLookup purchaseLookup;

    public ReviewService(ReviewRepository reviewRepository, PurchaseLookup purchaseLookup) {
        this.reviewRepository = reviewRepository;
        this.purchaseLookup = purchaseLookup;
    }

    public Review addReview(Long productId, String authorName, Integer rating, String comment) {
        boolean verifiedPurchase = purchaseLookup.hasPurchaseFor(productId);
        LOGGER.debug("Purchase verification for product {}: {}", productId, verifiedPurchase);
        Review review = new Review(productId, authorName == null ? "anonymous" : authorName, rating, comment);
        return reviewRepository.save(review);
    }

    public List<Review> listReviews(Long productId) {
        return reviewRepository.findByProductId(productId);
    }
}
