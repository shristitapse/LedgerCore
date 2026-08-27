package com.ledgercore.controller;

import com.ledgercore.dto.*;
import com.ledgercore.service.AccountService;
import com.ledgercore.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts", description = "Account management endpoints")
public class AccountController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    public AccountController(
            AccountService accountService,
            LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new account")
    public AccountResponse createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        return AccountResponse.from(
                accountService.createAccount(request.getName()));
    }

    @GetMapping
    @Operation(summary = "List all accounts")
    public List<AccountResponse> getAllAccounts() {
        return accountService.getAllAccounts().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    public AccountResponse getAccount(@PathVariable UUID id) {
        return AccountResponse.from(accountService.getAccount(id));
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get account balance (COMPLETED transactions only)")
    public BalanceResponse getBalance(@PathVariable UUID id) {
        return accountService.getBalance(id);
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Get transaction history for an account")
    public List<TransactionResponse> getTransactionHistory(
            @PathVariable UUID id) {
        return ledgerService.getTransactionHistory(id).stream()
                .map(TransactionResponse::from)
                .toList();
    }
}