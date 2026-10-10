package com.example.codebility_api.modules.account.web;

import com.example.codebility_api.modules.account.dto.AccountPaginationResponse;
import com.example.codebility_api.modules.account.dto.AccountProfileResponse;
import com.example.codebility_api.modules.account.dto.AccountQuery;
import com.example.codebility_api.modules.account.dto.CreateAccountRequest;
import com.example.codebility_api.modules.account.dto.MessageResponse;
import com.example.codebility_api.modules.account.dto.UpdateAccountRequest;
import com.example.codebility_api.modules.account.service.AccountQueryService;
import com.example.codebility_api.modules.account.service.AccountService;
import com.example.codebility_api.shared.security.AuthenticatedAccount;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts")
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountQueryService accountQueryService;

    public AccountController(AccountService accountService, AccountQueryService accountQueryService) {
        this.accountService = accountService;
        this.accountQueryService = accountQueryService;
    }

    @GetMapping("/me")
    public AccountProfileResponse me(@AuthenticationPrincipal AuthenticatedAccount caller) {
        return accountService.getCurrentAccount(caller);
    }

    @GetMapping
    public AccountPaginationResponse getAll(
            @RequestParam(required = false) String pagination,
            @RequestParam(required = false) String afterId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department) {
        return accountQueryService.getAccounts(new AccountQuery(
                pagination, afterId, page, limit, search, sortBy, sortOrder, role, status, department));
    }

    @GetMapping("/{id}")
    public AccountProfileResponse getById(
            @PathVariable String id, @AuthenticationPrincipal AuthenticatedAccount caller) {
        return accountService.getAccountById(id, caller);
    }

    @PostMapping
    public ResponseEntity<AccountProfileResponse> create(
            @Valid @RequestBody CreateAccountRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(body, request.getHeader(HttpHeaders.ORIGIN)));
    }

    @PutMapping("/{id}")
    public AccountProfileResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpdateAccountRequest body,
            @AuthenticationPrincipal AuthenticatedAccount caller) {
        return accountService.updateAccount(id, body, caller);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(
            @PathVariable String id, @AuthenticationPrincipal AuthenticatedAccount caller) {
        accountService.deleteAccount(id, caller);
        return new MessageResponse("Account deleted successfully");
    }
}
