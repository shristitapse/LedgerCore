package com.ledgercore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.entity.Account;
import com.ledgercore.entity.Entry;
import com.ledgercore.entity.EntryType;
import com.ledgercore.entity.LedgerTransaction;
import com.ledgercore.entity.TransactionStatus;
import com.ledgercore.repository.AccountRepository;
import com.ledgercore.repository.EntryRepository;
import com.ledgercore.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class LedgerCoreIntegrationTest {

        @Autowired
        WebApplicationContext webApplicationContext;

        MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Autowired
        AccountRepository accountRepository;

        @Autowired
        EntryRepository entryRepository;

        @Autowired
        LedgerTransactionRepository transactionRepository;

        @BeforeEach
        void cleanDatabase() {
                mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
                entryRepository.deleteAll();
                transactionRepository.deleteAll();
                accountRepository.deleteAll();
        }

        @Test
        void postAccountsCreatesAccountAndReturnsCreated() throws Exception {
                JsonNode response = createAccount("Wallet");

                assertThat(response.get("id").asText()).isNotBlank();
                assertThat(response.get("name").asText()).isEqualTo("Wallet");
        }

        @Test
        void getAccountsReturnsAllAccounts() throws Exception {
                createAccount("Wallet");
                createAccount("Savings");

                mockMvc.perform(get("/accounts"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(2)))
                                .andExpect(jsonPath("$[0].name").value("Wallet"))
                                .andExpect(jsonPath("$[1].name").value("Savings"));
        }

        @Test
        void getAccountReturnsAccount() throws Exception {
                UUID accountId = uuid(createAccount("Wallet"), "id");

                mockMvc.perform(get("/accounts/{id}", accountId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(accountId.toString()))
                                .andExpect(jsonPath("$.name").value("Wallet"));
        }

        @Test
        void getAccountWithInvalidIdReturnsNotFound() throws Exception {
                mockMvc.perform(get("/accounts/{id}", UUID.randomUUID()))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void blankAccountNameReturnsBadRequest() throws Exception {
                mockMvc.perform(post("/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"   \"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("Account name is required")));
        }

        @Test
        void accountNameLongerThan255CharactersReturnsBadRequest() throws Exception {
                String name = "a".repeat(256);

                mockMvc.perform(post("/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(accountJson(name)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("must not exceed 255")));
        }

        @Test
        void postTransactionsCreatesValidTwoEntryTransaction() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");

                JsonNode response = createTransaction("txn-two-entry", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                assertThat(response.get("status").asText()).isEqualTo("COMPLETED");
                assertThat(response.get("entries")).hasSize(2);
        }

        @Test
        void postTransactionsCreatesValidMultiEntryTransaction() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");
                UUID checkingId = uuid(createAccount("Checking"), "id");

                JsonNode response = createTransaction("txn-multi-entry", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 600, "CREDIT"), entry(checkingId, 400, "CREDIT"));

                assertThat(response.get("status").asText()).isEqualTo("COMPLETED");
                assertThat(response.get("entries")).hasSize(3);
        }

        @Test
        void unbalancedDebitAndCreditTotalsReturnBadRequest() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");

                postTransaction("txn-unbalanced", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 900, "CREDIT"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message",
                                                containsString("Debit and credit totals must be equal")));
        }

        @Test
        void transactionWithFewerThanTwoEntriesReturnsBadRequest() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");

                postTransaction("txn-one-entry", entry(walletId, 1000, "DEBIT"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("At least 2 entries are required")));
        }

        @Test
        void transactionWithoutDebitReturnsBadRequest() throws Exception {
                UUID savingsId = uuid(createAccount("Savings"), "id");
                UUID checkingId = uuid(createAccount("Checking"), "id");

                postTransaction("txn-no-debit", entry(savingsId, 600, "CREDIT"),
                                entry(checkingId, 600, "CREDIT"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("at least one DEBIT")));
        }

        @Test
        void transactionWithoutCreditReturnsBadRequest() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID cashId = uuid(createAccount("Cash"), "id");

                postTransaction("txn-no-credit", entry(walletId, 600, "DEBIT"),
                                entry(cashId, 600, "DEBIT"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("at least one CREDIT")));
        }

        @Test
        void nonexistentAccountReturnsNotFound() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID missingId = UUID.randomUUID();

                postTransaction("txn-missing-account", entry(walletId, 1000, "DEBIT"),
                                entry(missingId, 1000, "CREDIT"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message", containsString("Account not found")));
        }

        @Test
        void zeroAmountReturnsBadRequest() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");

                postTransaction("txn-zero-amount", entry(walletId, 0, "DEBIT"),
                                entry(savingsId, 0, "CREDIT"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("Amount must be greater than zero")));
        }

        @Test
        void negativeAmountReturnsBadRequest() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");

                postTransaction("txn-negative-amount", entry(walletId, -1000, "DEBIT"),
                                entry(savingsId, -1000, "CREDIT"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("Amount must be greater than zero")));
        }

        @Test
        void sameIdempotencyKeyReturnsSameTransaction() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");

                JsonNode first = createTransaction("txn-repeat", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));
                JsonNode second = createTransaction("txn-repeat", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());
        }

        @Test
        void repeatedIdempotentRequestDoesNotCreateDuplicateTransactions() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");

                createTransaction("txn-no-duplicate", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));
                createTransaction("txn-no-duplicate", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                assertThat(transactionRepository.count()).isEqualTo(1);
                assertThat(entryRepository.count()).isEqualTo(2);
        }

        @Test
        void databaseUniqueConstraintProtectsAgainstDuplicateIdempotencyKeys() {
                LedgerTransaction first = new LedgerTransaction("txn-db-unique");
                first.complete();
                transactionRepository.saveAndFlush(first);

                LedgerTransaction duplicate = new LedgerTransaction("txn-db-unique");
                duplicate.complete();

                assertThatThrownBy(() -> transactionRepository.saveAndFlush(duplicate))
                                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void balanceCalculatesCreditsMinusDebits() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");

                createTransaction("txn-balance", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                mockMvc.perform(get("/accounts/{id}/balance", walletId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.balance").value(-1000.0));
                mockMvc.perform(get("/accounts/{id}/balance", savingsId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.balance").value(1000.0));
        }

        @Test
        void pendingTransactionsAreExcludedFromBalance() throws Exception {
                Account wallet = accountRepository.save(new Account("Wallet"));
                LedgerTransaction pending = new LedgerTransaction("txn-pending");
                pending.addEntry(new Entry(wallet, BigDecimal.valueOf(1000), EntryType.CREDIT));
                transactionRepository.saveAndFlush(pending);

                mockMvc.perform(get("/accounts/{id}/balance", wallet.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.balance").value(0));
        }

        @Test
        void reversedTransactionsAreHandledCorrectlyInBalance() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");
                JsonNode original = createTransaction("txn-balance-reversal", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                mockMvc.perform(post("/transactions/{id}/reverse", uuid(original, "id")))
                                .andExpect(status().isCreated());

                mockMvc.perform(get("/accounts/{id}/balance", walletId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.balance").value(0.0));
                mockMvc.perform(get("/accounts/{id}/balance", savingsId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.balance").value(0.0));
        }

        @Test
        void getTransactionByIdWorks() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");
                JsonNode transaction = createTransaction("txn-get", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                mockMvc.perform(get("/transactions/{id}", uuid(transaction, "id")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(transaction.get("id").asText()))
                                .andExpect(jsonPath("$.entries", hasSize(2)));
        }

        @Test
        void accountTransactionHistoryReturnsDescendingCreatedAtOrder() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");

                JsonNode first = createTransaction("txn-history-1", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));
                Thread.sleep(5);
                JsonNode second = createTransaction("txn-history-2", entry(walletId, 500, "DEBIT"),
                                entry(savingsId, 500, "CREDIT"));

                mockMvc.perform(get("/accounts/{id}/transactions", walletId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(2)))
                                .andExpect(jsonPath("$[0].id").value(second.get("id").asText()))
                                .andExpect(jsonPath("$[1].id").value(first.get("id").asText()));
        }

        @Test
        void completedTransactionCanBeReversed() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");
                JsonNode original = createTransaction("txn-reversible", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                mockMvc.perform(post("/transactions/{id}/reverse", uuid(original, "id")))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        void originalTransactionBecomesReversed() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");
                JsonNode original = createTransaction("txn-original-reversed", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                JsonNode reversal = reverseTransaction(uuid(original, "id"));

                LedgerTransaction reloadedOriginal = transactionRepository.findById(uuid(original, "id")).orElseThrow();
                assertThat(reloadedOriginal.getStatus()).isEqualTo(TransactionStatus.REVERSED);
                assertThat(reloadedOriginal.getReversedTransactionId()).isEqualTo(uuid(reversal, "id"));
        }

        @Test
        void reversalTransactionIsCompleted() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");
                JsonNode original = createTransaction("txn-reversal-completed", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                JsonNode reversal = reverseTransaction(uuid(original, "id"));

                assertThat(reversal.get("status").asText()).isEqualTo("COMPLETED");
        }

        @Test
        void reversalEntriesAreOppositeOfOriginalEntries() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");
                JsonNode original = createTransaction("txn-opposite", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                JsonNode reversal = reverseTransaction(uuid(original, "id"));
                List<JsonNode> sortedOriginal = sortedEntries(original);
                List<JsonNode> sortedReversal = sortedEntries(reversal);

                assertThat(sortedReversal).hasSize(sortedOriginal.size());
                for (int i = 0; i < sortedOriginal.size(); i++) {
                        assertThat(sortedReversal.get(i).get("accountId").asText())
                                        .isEqualTo(sortedOriginal.get(i).get("accountId").asText());
                        assertThat(sortedReversal.get(i).get("amount").decimalValue())
                                        .isEqualByComparingTo(sortedOriginal.get(i).get("amount").decimalValue());
                        assertThat(sortedReversal.get(i).get("type").asText())
                                        .isEqualTo(oppositeType(sortedOriginal.get(i).get("type").asText()));
                }
        }

        @Test
        void reversingAlreadyReversedTransactionFails() throws Exception {
                UUID walletId = uuid(createAccount("Wallet"), "id");
                UUID savingsId = uuid(createAccount("Savings"), "id");
                JsonNode original = createTransaction("txn-already-reversed", entry(walletId, 1000, "DEBIT"),
                                entry(savingsId, 1000, "CREDIT"));

                reverseTransaction(uuid(original, "id"));

                mockMvc.perform(post("/transactions/{id}/reverse", uuid(original, "id")))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message", containsString("already reversed")));
        }

        @Test
        void reversingNonexistentTransactionReturnsNotFound() throws Exception {
                mockMvc.perform(post("/transactions/{id}/reverse", UUID.randomUUID()))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value(404));
        }

        private JsonNode createAccount(String name) throws Exception {
                String response = mockMvc.perform(post("/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(accountJson(name)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                return objectMapper.readTree(response);
        }

        private String accountJson(String name) throws Exception {
                return objectMapper.writeValueAsString(new AccountPayload(name));
        }

        private JsonNode createTransaction(String idempotencyKey, EntryPayload... entries) throws Exception {
                String response = postTransaction(idempotencyKey, entries)
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                return objectMapper.readTree(response);
        }

        private org.springframework.test.web.servlet.ResultActions postTransaction(
                        String idempotencyKey, EntryPayload... entries) throws Exception {

                TransactionPayload payload = new TransactionPayload(idempotencyKey, List.of(entries));
                return mockMvc.perform(post("/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(payload)));
        }

        private JsonNode reverseTransaction(UUID transactionId) throws Exception {
                String response = mockMvc.perform(post("/transactions/{id}/reverse", transactionId))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                return objectMapper.readTree(response);
        }

        private EntryPayload entry(UUID accountId, int amount, String type) {
                return new EntryPayload(accountId, BigDecimal.valueOf(amount), type);
        }

        private UUID uuid(JsonNode node, String field) {
                return UUID.fromString(node.get(field).asText());
        }

        private List<JsonNode> sortedEntries(JsonNode transaction) {
                return transaction.get("entries")
                                .findValues("accountId")
                                .stream()
                                .map(accountId -> findEntry(transaction, accountId.asText()))
                                .sorted(Comparator.comparing(entry -> entry.get("accountId").asText()))
                                .toList();
        }

        private JsonNode findEntry(JsonNode transaction, String accountId) {
                for (JsonNode entry : transaction.get("entries")) {
                        if (entry.get("accountId").asText().equals(accountId)) {
                                return entry;
                        }
                }
                throw new IllegalArgumentException("Entry not found for account " + accountId);
        }

        private String oppositeType(String type) {
                return "DEBIT".equals(type) ? "CREDIT" : "DEBIT";
        }

        private record AccountPayload(String name) {
        }

        private record TransactionPayload(String idempotencyKey, List<EntryPayload> entries) {
        }

        private record EntryPayload(UUID accountId, BigDecimal amount, String type) {
        }
}
