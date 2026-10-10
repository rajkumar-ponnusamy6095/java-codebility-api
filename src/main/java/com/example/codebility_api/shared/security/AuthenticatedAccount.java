package com.example.codebility_api.shared.security;

public record AuthenticatedAccount(String id, String email, String role) {

    public static final String ROLE_ADMIN = "Admin";
    public static final String ROLE_USER = "User";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean canAccess(String accountId) {
        return isAdmin() || id.equals(accountId);
    }
}
