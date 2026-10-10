package com.example.codebility_api.modules.account.dto;

/** Query parameters accepted by GET /accounts. */
public record AccountQuery(
        String pagination,
        String afterId,
        Integer page,
        Integer limit,
        String search,
        String sortBy,
        String sortOrder,
        String role,
        String status,
        String department
) {}
