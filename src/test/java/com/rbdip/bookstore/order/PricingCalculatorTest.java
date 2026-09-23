package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @Test
    void appliesVolumeDiscountOnlyAboveTenItems() {
        assertThat(total(item("10.00", 10), "regular", null)).isEqualByComparingTo("100.00");
        assertThat(total(item("10.00", 11), "regular", null)).isEqualByComparingTo("104.50");
    }

    @Test
    void appliesCustomerDiscounts() {
        assertThat(total(item("100.00", 1), "vip", null)).isEqualByComparingTo("90.00");
        assertThat(total(item("100.00", 1), "wholesale", null)).isEqualByComparingTo("85.00");
        assertThat(total(item("100.00", 1), "unknown", null)).isEqualByComparingTo("100.00");
    }

    @Test
    void appliesSupportedCoupons() {
        assertThat(total(item("100.00", 1), "regular", "SAVE10")).isEqualByComparingTo("90.00");
        assertThat(total(item("100.00", 1), "regular", "SAVE20PERCENT"))
                .isEqualByComparingTo("80.00");
        assertThat(total(item("100.00", 1), "regular", "UNKNOWN"))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void neverReturnsNegativeTotal() {
        assertThat(total(item("5.00", 1), "regular", "SAVE10")).isEqualByComparingTo("0.00");
    }

    @Test
    void appliesLargeOrderDiscountOnlyAboveThreshold() {
        assertThat(total(item("1000.00", 1), "regular", null)).isEqualByComparingTo("1000.00");
        assertThat(total(item("1000.01", 1), "regular", null)).isEqualByComparingTo("980.01");
    }

    @Test
    void sumsLinesAndRoundsMonetaryResult() {
        BigDecimal result = calculator.calculateOrderTotal(
                List.of(item("10.005", 1), item("0.001", 1)), "regular", null);

        assertThat(result).isEqualByComparingTo("10.01");
    }

    @Test
    void returnsZeroForEmptyOrder() {
        assertThat(calculator.calculateOrderTotal(List.of(), "regular", null))
                .isEqualByComparingTo("0.00");
    }

    private BigDecimal total(PricingCalculator.LineItem item, String customerType, String couponCode) {
        return calculator.calculateOrderTotal(List.of(item), customerType, couponCode);
    }

    private PricingCalculator.LineItem item(String price, int quantity) {
        return new PricingCalculator.LineItem(new BigDecimal(price), quantity);
    }
}
