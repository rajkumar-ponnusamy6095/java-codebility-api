package com.example.codebility_api.modules.account.service;

final class AccountFields {

    private AccountFields() {
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
