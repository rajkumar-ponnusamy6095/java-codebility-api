package com.example.codebility_api.modules.account.domain;

import com.example.codebility_api.shared.security.AuthenticatedAccount;

public final class Role {

    public static final String ADMIN = AuthenticatedAccount.ROLE_ADMIN;
    public static final String USER = AuthenticatedAccount.ROLE_USER;

    private Role() {
    }
}
