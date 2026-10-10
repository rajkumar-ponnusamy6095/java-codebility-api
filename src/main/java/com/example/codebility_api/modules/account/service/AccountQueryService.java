package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.dto.AccountPaginationResponse;
import com.example.codebility_api.modules.account.dto.AccountProfileResponse;
import com.example.codebility_api.modules.account.dto.AccountQuery;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Account listing with offset ("page") or keyset ("cursor") pagination.
 * Cursor mode walks the _id index and never counts, so it stays fast on very
 * large collections. Only fields needed for the response are loaded.
 */
@Service
public class AccountQueryService {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 100;

    private static final Set<String> SORT_FIELDS = Set.of(
            "id", "gender", "firstName", "lastName", "email", "phone",
            "department", "status", "role", "createdAt", "updated");

    private static final String[] PROFILE_FIELDS = {
            "gender", "firstName", "lastName", "email", "phone", "department",
            "status", "role", "createdAt", "updated", "verified", "passwordReset"};

    private final MongoTemplate mongoTemplate;

    public AccountQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public AccountPaginationResponse getAccounts(AccountQuery params) {
        String mode = lower(params.pagination(), "page");
        if (!mode.equals("page") && !mode.equals("cursor")) {
            throw badRequest("pagination must be page or cursor");
        }
        String sortField = params.sortBy() == null || params.sortBy().isBlank() ? "id" : params.sortBy();
        if (!SORT_FIELDS.contains(sortField)) {
            throw badRequest("Unsupported sortBy field");
        }
        String order = lower(params.sortOrder(), "asc");
        if (!order.equals("asc") && !order.equals("desc")) {
            throw badRequest("sortOrder must be asc or desc");
        }
        Sort.Direction direction = order.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        int limit = clamp(params.limit() == null ? DEFAULT_LIMIT : params.limit(), 1, MAX_LIMIT);
        boolean hasAfterId = params.afterId() != null && !params.afterId().isBlank();

        List<Criteria> filters = buildFilters(params);

        if (mode.equals("page")) {
            if (hasAfterId) {
                throw badRequest("afterId requires pagination=cursor");
            }
            int page = Math.max(1, params.page() == null ? 1 : params.page());
            Query query = withFilters(filters);
            long total = mongoTemplate.count(query, Account.class);
            query.with(Sort.by(direction, sortField).and(Sort.by(direction, "id")))
                    .skip((long) (page - 1) * limit)
                    .limit(limit);
            List<AccountProfileResponse> data = find(query);
            int totalPages = (int) Math.ceil((double) total / limit);
            return new AccountPaginationResponse(data, new AccountPaginationResponse.PaginationMetadata(
                    page, limit, total, totalPages, page < totalPages, null));
        }

        if (!sortField.equals("id")) {
            throw badRequest("Cursor pagination requires sortBy=id");
        }
        if (hasAfterId) {
            if (!ObjectId.isValid(params.afterId())) {
                throw badRequest("Invalid afterId");
            }
            ObjectId afterId = new ObjectId(params.afterId());
            filters.add(direction == Sort.Direction.ASC
                    ? Criteria.where("id").gt(afterId)
                    : Criteria.where("id").lt(afterId));
        }
        Query query = withFilters(filters).with(Sort.by(direction, "id")).limit(limit + 1);
        List<AccountProfileResponse> data = new ArrayList<>(find(query));
        boolean hasMore = data.size() > limit;
        if (hasMore) {
            data = new ArrayList<>(data.subList(0, limit));
        }
        String nextCursor = hasMore ? data.get(data.size() - 1).id() : null;
        return new AccountPaginationResponse(data, new AccountPaginationResponse.PaginationMetadata(
                null, limit, null, null, hasMore, nextCursor));
    }

    private List<Criteria> buildFilters(AccountQuery params) {
        List<Criteria> filters = new ArrayList<>();
        if (params.search() != null && !params.search().isBlank()) {
            String pattern = Pattern.quote(params.search().trim());
            Document fullName = new Document("$concat", List.of(
                    new Document("$ifNull", List.of("$firstName", "")),
                    " ",
                    new Document("$ifNull", List.of("$lastName", ""))));
            Document fullNameMatch = new Document("$regexMatch", new Document("input", fullName)
                    .append("regex", pattern)
                    .append("options", "i"));
            filters.add(new Criteria().orOperator(
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("phone").regex(pattern, "i"),
                    Criteria.where("department").regex(pattern, "i"),
                    Criteria.where("$expr").is(fullNameMatch)));
        }
        if (params.role() != null && !params.role().isBlank()) {
            filters.add(Criteria.where("role").is(params.role()));
        }
        if (params.status() != null && !params.status().isBlank()) {
            filters.add(Criteria.where("status").is(params.status()));
        }
        if (params.department() != null && !params.department().isBlank()) {
            filters.add(Criteria.where("department").is(params.department()));
        }
        return filters;
    }

    private static Query withFilters(List<Criteria> filters) {
        return filters.isEmpty()
                ? new Query()
                : new Query(new Criteria().andOperator(filters.toArray(new Criteria[0])));
    }

    private List<AccountProfileResponse> find(Query query) {
        query.fields().include(PROFILE_FIELDS);
        return mongoTemplate.find(query, Account.class).stream()
                .map(AccountMapper::toProfile)
                .toList();
    }

    private static String lower(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.toLowerCase(Locale.ROOT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
