package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Модуль расчёта цены заказа. Намеренно почти не покрыт тестами и
 * содержит magic numbers / нечитаемые ветвления скидок - цель для
 * характеризационных тестов (ЛР2) и mutation-testing гейта PIT (ЛР5).
 */
public class PricingCalculator {

    // типы клиентов
    private static final String VIP_CUSTOMER = "vip";
    private static final String WHOLESALE_CUSTOMER = "wholesale";

    // коды купонов
    private static final String FIXED_DISCOUNT_COUPON = "SAVE10";
    private static final String PERCENT_DISCOUNT_COUPON = "SAVE20PERCENT";

    // скидка за объем
    private static final int MIN_VOLUME_DISCOUNT_QUANTITY = 10;
    private static final BigDecimal BULK_MULTIPLIER = new BigDecimal("0.95");

    // скидки по типу клиента
    private static final BigDecimal VIP_MULTIPLIER = new BigDecimal("0.9");
    private static final BigDecimal WHOLESALE_MULTIPLIER = new BigDecimal("0.85");

    // купоны
    private static final BigDecimal SAVE10_DISCOUNT = BigDecimal.TEN;
    private static final BigDecimal SAVE20_MULTIPLIER = new BigDecimal("0.8");

    // крупные заказы
    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal LARGE_ORDER_MULTIPLIER = new BigDecimal("0.98");

    // другое
    private static final int MONEY_SCALE = 2;

    public record LineItem(BigDecimal price, int quantity) {
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal total = BigDecimal.ZERO;

        for (LineItem item : items) {
            BigDecimal linePrice = item.price().multiply(BigDecimal.valueOf(item.quantity()));
            if (item.quantity() > MIN_VOLUME_DISCOUNT_QUANTITY) {
                linePrice = linePrice.multiply(BULK_MULTIPLIER);
            }
            total = total.add(linePrice);
        }

        if (VIP_CUSTOMER.equals(customerType)) {
            total = total.multiply(VIP_MULTIPLIER);
        } else if (WHOLESALE_CUSTOMER.equals(customerType)) {
            total = total.multiply(WHOLESALE_MULTIPLIER);
        }

        if (FIXED_DISCOUNT_COUPON.equals(couponCode)) {
            total = total.subtract(SAVE10_DISCOUNT);
        } else if (PERCENT_DISCOUNT_COUPON.equals(couponCode)) {
            total = total.multiply(SAVE20_MULTIPLIER);
        }

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        if (total.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            total = total.multiply(LARGE_ORDER_MULTIPLIER);
        }

        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
