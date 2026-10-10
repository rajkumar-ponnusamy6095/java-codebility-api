package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.dto.AccountProfileResponse;

public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountProfileResponse toProfile(Account account) {
        return new AccountProfileResponse(
                account.getId(),
                account.getGender(),
                account.getFirstName(),
                account.getLastName(),
                account.getEmail(),
                account.getPhone(),
                account.getDepartment(),
                account.getStatus(),
                account.getRole(),
                account.getCreatedAt(),
                account.getUpdated(),
                account.isVerified());
    }
}
