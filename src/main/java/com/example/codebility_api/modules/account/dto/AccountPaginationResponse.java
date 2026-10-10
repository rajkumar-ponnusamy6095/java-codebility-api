package com.example.codebility_api.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record AccountPaginationResponse(
        List<AccountProfileResponse> data,
        PaginationMetadata pagination
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaginationMetadata(
            Integer page,
            Integer limit,
            Long total,
            Integer totalPages,
            Boolean hasMore,
            String nextCursor
    ) {}
}
