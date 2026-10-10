
package com.example.codebility_api.dto;

import java.util.List;

public record AccountListResponse(
        List<LoginResponse.AccountDetails> accounts,
        long total
) {
}
