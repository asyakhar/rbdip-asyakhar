package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.BookstoreApplication;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OrderApiDuringMigrationTest {

    private static final int WORKER_COUNT = 4;
    private static final int REQUIRED_POST_MIGRATION_REQUESTS = 10;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bookstore_api_migration")
            .withUsername("bookstore")
            .withPassword("bookstore");

    @Test
    void orderApiRemainsAvailableDuringContractMigration() throws Exception {
        migrateToVersionThree();

        try (ConfigurableApplicationContext context = startApplication()) {
            RestTemplate restTemplate = createRestTemplate();
            String baseUrl = "http://localhost:" + serverPort(context);
            Long productId = createProduct(restTemplate, baseUrl);

            List<String> errors = new CopyOnWriteArrayList<>();
            AtomicBoolean keepSending = new AtomicBoolean(true);
            AtomicInteger successfulRequests = new AtomicInteger();
            AtomicInteger inFlightRequests = new AtomicInteger();
            CountDownLatch workersStarted = new CountDownLatch(WORKER_COUNT);
            ExecutorService workers = Executors.newFixedThreadPool(WORKER_COUNT);
            ExecutorService migrationWorker = Executors.newSingleThreadExecutor();
            CountDownLatch contractApplied = new CountDownLatch(1);
            CountDownLatch allowContractCommit = new CountDownLatch(1);

            for (int worker = 0; worker < WORKER_COUNT; worker++) {
                workers.submit(() -> sendOrders(
                        restTemplate,
                        baseUrl,
                        productId,
                        keepSending,
                        successfulRequests,
                        inFlightRequests,
                        workersStarted,
                        errors));
            }

            try {
                assertThat(workersStarted.await(5, TimeUnit.SECONDS)).isTrue();
                awaitSuccessfulRequests(successfulRequests, WORKER_COUNT, Duration.ofSeconds(10));

                Future<?> migration = migrationWorker.submit(() -> migrateToLatest(
                        contractApplied, allowContractCommit));
                assertThat(contractApplied.await(10, TimeUnit.SECONDS)).isTrue();
                awaitAtLeast(inFlightRequests, 1, Duration.ofSeconds(2));
                allowContractCommit.countDown();
                migration.get(10, TimeUnit.SECONDS);

                awaitSuccessfulRequests(
                        successfulRequests,
                        REQUIRED_POST_MIGRATION_REQUESTS,
                        Duration.ofSeconds(10));
            } finally {
                allowContractCommit.countDown();
                keepSending.set(false);
                workers.shutdown();
                assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                migrationWorker.shutdown();
                assertThat(migrationWorker.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }

            assertThat(successfulRequests.get()).isGreaterThanOrEqualTo(
                    WORKER_COUNT + REQUIRED_POST_MIGRATION_REQUESTS);
            assertThat(errors).as("HTTP errors, 5xx responses, or request timeouts during migration")
                    .isEmpty();
        }
    }

    private void migrateToVersionThree() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("3")
                .load()
                .migrate();
    }

    private ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(BookstoreApplication.class)
                .properties(Map.of("spring.main.web-application-type", "servlet"))
                .run("--server.port=0",
                        "--spring.flyway.enabled=false",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword());
    }

    private int serverPort(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);
        return new RestTemplate(factory);
    }

    private Long createProduct(RestTemplate restTemplate, String baseUrl) {
        Map<?, ?> response = restTemplate.postForObject(
                baseUrl + "/products",
                Map.of("name", "Load Test Book", "price", new BigDecimal("25.00")),
                Map.class);
        return Long.valueOf(response.get("id").toString());
    }

    private void sendOrders(
            RestTemplate restTemplate,
            String baseUrl,
            Long productId,
            AtomicBoolean keepSending,
            AtomicInteger successfulRequests,
            AtomicInteger inFlightRequests,
            CountDownLatch workersStarted,
            List<String> errors) {
        workersStarted.countDown();
        while (keepSending.get()) {
            inFlightRequests.incrementAndGet();
            try {
                var response = restTemplate.postForEntity(
                        baseUrl + "/orders",
                        Map.of(
                                "customerFullName", "Load Test Customer",
                                "customerAddress", "Test Address",
                                "items", List.of(Map.of("productId", productId, "quantity", 1))),
                        Map.class);

                if (response.getStatusCode() != HttpStatus.CREATED) {
                    errors.add("Unexpected HTTP status " + response.getStatusCode());
                } else {
                    successfulRequests.incrementAndGet();
                }
            } catch (Exception e) {
                errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                inFlightRequests.decrementAndGet();
            }
        }
    }

    private void migrateToLatest(CountDownLatch contractApplied, CountDownLatch allowContractCommit) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .callbacks(new BaseCallback() {
                    @Override
                    public boolean supports(Event event, Context context) {
                        return event == Event.AFTER_EACH_MIGRATE
                                && "4".equals(context.getMigrationInfo().getVersion().getVersion());
                    }

                    @Override
                    public boolean canHandleInTransaction(Event event, Context context) {
                        return true;
                    }

                    @Override
                    public void handle(Event event, Context context) {
                        contractApplied.countDown();
                        try {
                            if (!allowContractCommit.await(5, TimeUnit.SECONDS)) {
                                throw new FlywayException("Timed out waiting for concurrent API requests");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new FlywayException("Interrupted while synchronizing API load", e);
                        }
                    }
                })
                .load()
                .migrate();
    }

    private void awaitSuccessfulRequests(AtomicInteger successfulRequests, int expected, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (successfulRequests.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(successfulRequests.get()).isGreaterThanOrEqualTo(expected);
    }

    private void awaitAtLeast(AtomicInteger counter, int expected, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (counter.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(counter.get()).isGreaterThanOrEqualTo(expected);
    }
}
