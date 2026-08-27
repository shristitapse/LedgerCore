package com.ledgercore.controller;

import com.ledgercore.dto.CreateTransactionRequest;
import com.ledgercore.dto.TransactionResponse;
import com.ledgercore.entity.LedgerTransaction;
import com.ledgercore.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "Transactions", description = "Transaction management endpoints")
public class TransactionController {

    private final LedgerService ledgerService;

    public TransactionController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new multi-entry transaction")
    public TransactionResponse createTransaction(
            @Valid @RequestBody CreateTransactionRequest request) {

        LedgerTransaction transaction = ledgerService.createTransaction(request);

        return TransactionResponse.from(transaction);
    }

    @GetMapping("/transactions/{id}")
    @Operation(summary = "Get transaction by ID")
    public TransactionResponse getTransaction(
            @PathVariable UUID id) {

        return TransactionResponse.from(
                ledgerService.getTransaction(id));
    }

    @PostMapping("/transactions/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reverse a completed transaction")
    public TransactionResponse reverse(
            @PathVariable UUID id) {

        return TransactionResponse.from(
                ledgerService.reverse(id));
    }
}