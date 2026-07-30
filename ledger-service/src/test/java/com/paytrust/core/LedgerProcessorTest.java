package com.paytrust.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytrust.core.domain.Account;
import com.paytrust.core.domain.LedgerEvent;
import com.paytrust.core.dto.TransactionEvent;
import com.paytrust.core.repository.AccountRepository;
import com.paytrust.core.repository.LedgerEventRepository;
import com.paytrust.core.service.LedgerProcessor;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Test suite for LedgerProcessor.
 *
 * Strategy
 * --------
 * These are INTEGRATION tests: we spin up the full Spring context with:
 *   - H2 (in-memory)    → replaces PostgreSQL; no Docker/external DB needed
 *   - EmbeddedKafka     → replaces the real broker; messages travel the same
 *                         code path as production (Kafka → @KafkaListener → DB)
 *
 * Why not plain unit tests with Mockito?
 *   The most dangerous bugs in a ledger live at the boundaries:
 *   the @Transactional rollback, the unique constraint on idempotency_key,
 *   and the OCC @Version column. Mocks would hide all three.
 *   Integration tests catch them for free.
 *
 * Test isolation
 * --------------
 * @DirtiesContext resets the Spring context (and therefore H2) between tests
 * so no test can pollute another's account state.
 * Each test seeds its own Account row in @BeforeEach.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Point the listener and template at the embedded broker
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@EmbeddedKafka(
        partitions = 1,
        topics = {"ledger-transactions"},
        // Expose the broker address so the property above can resolve it
        brokerPropertiesLocation = ""
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("LedgerProcessor — Integration Tests")
class LedgerProcessorTest {

    // -----------------------------------------------------------------------
    // Test constants
    // -----------------------------------------------------------------------

    private static final String TOPIC          = "ledger-transactions";
    private static final String ACCOUNT_ID     = "ACC-001";
    private static final String CURRENCY       = "USD";

    /** How long to wait for the async Kafka consumer to commit to H2. */
    private static final int KAFKA_TIMEOUT_SECONDS = 10;

    // -----------------------------------------------------------------------
    // Spring-injected collaborators
    // -----------------------------------------------------------------------

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private AccountRepository             accountRepository;
    @Autowired private LedgerEventRepository         ledgerEventRepository;
    @Autowired private LedgerProcessor               ledgerProcessor;

    private final ObjectMapper json = new ObjectMapper();

    // -----------------------------------------------------------------------
    // Shared setup — seed a fresh account before every test
    // -----------------------------------------------------------------------

    @BeforeEach
    void seedAccount() {
        Account account = new Account();
        account.id       = ACCOUNT_ID;
        account.balance  = new BigDecimal("500.00");
        account.currency = CURRENCY;
        accountRepository.save(account);
    }

    // =======================================================================
    // TEST 1 — Successful DEPOSIT
    // =======================================================================

    /**
     * GIVEN  an account with a $500 balance
     * WHEN   a DEPOSIT of $200 is published to Kafka
     * THEN   the account balance becomes $700
     *  AND   one LedgerEvent row is appended with the correct fields
     */
    @Test
    @DisplayName("DEPOSIT: balance increases by the deposited amount")
    void deposit_shouldIncreaseBalance() throws Exception {

        // Arrange
        String idempotencyKey = "idem-deposit-001";
        String message = buildMessage(idempotencyKey, ACCOUNT_ID, "200.00", CURRENCY, "DEPOSIT");

        // Act — publish to Kafka; the @KafkaListener picks it up asynchronously
        kafkaTemplate.send(TOPIC, message);

        // Assert — poll until the DB reflects the change (or time out and fail)
        await().atMost(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .untilAsserted(() -> {

                    // Balance check
                    Account updated = accountRepository.findById(ACCOUNT_ID).orElseThrow();
                    assertThat(updated.balance)
                            .as("Balance after $200 deposit into $500 account")
                            .isEqualByComparingTo("700.00");

                    // Audit-trail check — exactly one event row must exist
                    Iterable<LedgerEvent> events = ledgerEventRepository.findAll();
                    assertThat(events)
                            .as("Exactly one LedgerEvent must be recorded")
                            .hasSize(1);

                    LedgerEvent event = events.iterator().next();
                    assertThat(event.accountId)       .isEqualTo(ACCOUNT_ID);
                    assertThat(event.amount)           .isEqualByComparingTo("200.00");
                    assertThat(event.transactionType)  .isEqualToIgnoringCase("DEPOSIT");
                    assertThat(event.idempotencyKey)   .isEqualTo(idempotencyKey);
                });
    }

    // =======================================================================
    // TEST 2 — Successful WITHDRAWAL
    // =======================================================================

    /**
     * GIVEN  an account with a $500 balance
     * WHEN   a WITHDRAWAL of $150 is published to Kafka
     * THEN   the account balance becomes $350
     *  AND   one LedgerEvent row is appended
     */
    @Test
    @DisplayName("WITHDRAWAL: balance decreases by the withdrawn amount")
    void withdrawal_shouldDecreaseBalance() throws Exception {

        // Arrange
        String idempotencyKey = "idem-withdrawal-001";
        String message = buildMessage(idempotencyKey, ACCOUNT_ID, "150.00", CURRENCY, "WITHDRAWAL");

        // Act
        kafkaTemplate.send(TOPIC, message);

        // Assert
        await().atMost(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .untilAsserted(() -> {

                    Account updated = accountRepository.findById(ACCOUNT_ID).orElseThrow();
                    assertThat(updated.balance)
                            .as("Balance after $150 withdrawal from $500 account")
                            .isEqualByComparingTo("350.00");

                    Iterable<LedgerEvent> events = ledgerEventRepository.findAll();
                    assertThat(events).hasSize(1);

                    LedgerEvent event = events.iterator().next();
                    assertThat(event.transactionType).isEqualToIgnoringCase("WITHDRAWAL");
                    assertThat(event.amount)          .isEqualByComparingTo("150.00");
                    assertThat(event.idempotencyKey)  .isEqualTo(idempotencyKey);
                });
    }

    // =======================================================================
    // TEST 3 — Overdraft rejection
    // =======================================================================

    /**
     * GIVEN  an account with a $500 balance
     * WHEN   a WITHDRAWAL of $999 (more than balance) is published to Kafka
     * THEN   the account balance remains $500 — unchanged
     *  AND   zero LedgerEvent rows are written (transaction was silently rejected)
     *
     * NOTE   The current implementation logs a warning and returns early without
     *        saving.  This test pins that behaviour so a future refactor cannot
     *        accidentally allow overdrafts.
     */
    @Test
    @DisplayName("OVERDRAFT: balance unchanged; no event persisted")
    void withdrawal_overdraft_shouldRejectAndLeaveBalanceUnchanged() throws Exception {

        // Arrange — amount exceeds balance by a large margin
        String idempotencyKey = "idem-overdraft-001";
        String message = buildMessage(idempotencyKey, ACCOUNT_ID, "999.00", CURRENCY, "WITHDRAWAL");

        // Act
        kafkaTemplate.send(TOPIC, message);

        /*
         * Awaitility is still used here, but the assertion is NEGATIVE:
         * we expect the balance to stay at 500 and no rows to appear.
         * We give the consumer time to process the message, then verify
         * nothing was written.  A brief sleep before the assertion ensures
         * the consumer has had a reasonable chance to act.
         */
        // Give the consumer enough time to process the message (and potentially
        // write a row we do NOT want).
        Thread.sleep(3_000);

        Account unchanged = accountRepository.findById(ACCOUNT_ID).orElseThrow();
        assertThat(unchanged.balance)
                .as("Balance must NOT change when withdrawal exceeds funds")
                .isEqualByComparingTo("500.00");

        long eventCount = countEvents();
        assertThat(eventCount)
                .as("No LedgerEvent must be persisted for a rejected overdraft")
                .isZero();
    }

    // =======================================================================
    // TEST 4 — Idempotency (duplicate message ignored)
    // =======================================================================

    /**
     * GIVEN  an account with a $500 balance
     * WHEN   the SAME deposit message (same idempotency_key) is published TWICE
     * THEN   the balance is incremented only once ($700, not $900)
     *  AND   the database rejects the second insert via the UNIQUE constraint
     *        on ledger_events.idempotency_key, preventing double-counting
     *
     * This test validates the "exactly-once" semantic guarantee.
     * The @Column(unique=true) on idempotencyKey is what enforces this at the
     * DB level — the test makes that constraint visible and proven.
     */
    @Test
    @DisplayName("IDEMPOTENCY: duplicate message is ignored; balance updated once")
    void deposit_duplicateMessage_shouldBeIgnoredByIdempotencyKey() throws Exception {

        // Arrange — both messages carry the SAME idempotency key
        String idempotencyKey = "idem-dedup-001";
        String message = buildMessage(idempotencyKey, ACCOUNT_ID, "200.00", CURRENCY, "DEPOSIT");

        // Act — send the same message twice (simulates Kafka at-least-once redelivery)
        kafkaTemplate.send(TOPIC, message);

        // Wait for the first message to be fully committed before sending the duplicate
        await().atMost(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(countEvents()).as("First event must be persisted").isEqualTo(1)
                );

        // Now send the duplicate
        kafkaTemplate.send(TOPIC, message);

        // Give the consumer time to attempt (and fail) the second insert
        Thread.sleep(3_000);

        // Assert — balance reflects exactly one deposit
        Account account = accountRepository.findById(ACCOUNT_ID).orElseThrow();
        assertThat(account.balance)
                .as("Balance must reflect exactly ONE deposit of $200, not two")
                .isEqualByComparingTo("700.00");

        // Assert — only one event row exists; the duplicate was swallowed
        assertThat(countEvents())
                .as("Only one LedgerEvent must exist despite two messages")
                .isEqualTo(1);
    }

    // =======================================================================
    // TEST 5 — Unit-style test: LedgerProcessor in isolation via Mockito
    // =======================================================================
    // (Kept as a pure unit test to show the two complementary styles.)

    /**
     * GIVEN  no matching account in the repository
     * WHEN   processTransaction is called directly (not via Kafka)
     * THEN   an IllegalArgumentException is raised inside the processor
     *  AND   no event is persisted
     *
     * This test exercises the processor method directly without going through
     * Kafka, making it fast and deterministic for the "account not found" path.
     */
    @Test
    @DisplayName("UNIT — unknown account: no event persisted, no balance change")
    void processTransaction_unknownAccount_shouldNotPersistEvent() throws Exception {

        // Arrange — use an account ID that was never seeded
        String message = buildMessage("idem-unknown-001", "NONEXISTENT", "100.00", CURRENCY, "DEPOSIT");

        // Act & Assert — calling directly throws exception
        assertThatThrownBy(() -> ledgerProcessor.processTransaction(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Rolling back transaction for retry");

        // Assert — nothing was written
        assertThat(countEvents())
                .as("No event should be persisted when account does not exist")
                .isZero();
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    /** Serialises a TransactionEvent to JSON manually to avoid test coupling. */
    private String buildMessage(String key, String accountId,
                                String amount, String currency,
                                String type) throws Exception {
        TransactionEvent e = new TransactionEvent();
        e.idempotencyKey  = key;
        e.accountId       = accountId;
        e.amount          = new BigDecimal(amount);
        e.currency        = currency;
        e.transactionType = type;
        return json.writeValueAsString(e);
    }

    /** Returns the total number of rows in ledger_events. */
    private long countEvents() {
        long count = 0;
        for (LedgerEvent ignored : ledgerEventRepository.findAll()) count++;
        return count;
    }
}