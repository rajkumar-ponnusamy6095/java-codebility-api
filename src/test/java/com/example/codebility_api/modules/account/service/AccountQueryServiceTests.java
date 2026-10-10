package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.dto.AccountPaginationResponse;
import com.example.codebility_api.modules.account.dto.AccountQuery;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountQueryServiceTests {

    private MongoTemplate mongoTemplate;
    private AccountQueryService service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        service = new AccountQueryService(mongoTemplate);
    }

    private static AccountQuery cursor(String afterId, Integer limit, String order) {
        return new AccountQuery("cursor", afterId, null, limit, null, null, order, null, null, null);
    }

    private static Account account(int i) {
        Account account = new Account();
        account.setId(String.format("64b0000000000000000000%02x", i));
        return account;
    }

    @Test
    void cursorPageFetchesLimitPlusOneAndReturnsNextCursor() {
        List<Account> rows = IntStream.rangeClosed(1, 3).mapToObj(AccountQueryServiceTests::account).toList();
        when(mongoTemplate.find(any(Query.class), eq(Account.class))).thenReturn(rows);

        AccountPaginationResponse response = service.getAccounts(cursor(null, 2, null));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Account.class));
        assertThat(query.getValue().getLimit()).isEqualTo(3);
        assertThat(query.getValue().getFieldsObject()).doesNotContainKey("passwordHash").containsKey("email");
        assertThat(response.data()).hasSize(2);
        assertThat(response.pagination().hasMore()).isTrue();
        assertThat(response.pagination().nextCursor()).isEqualTo(rows.get(1).getId());
        verify(mongoTemplate, never()).count(any(Query.class), eq(Account.class));
    }

    @Test
    void descendingCursorUsesLessThan() {
        when(mongoTemplate.find(any(Query.class), eq(Account.class))).thenReturn(List.of());

        service.getAccounts(cursor("64b000000000000000000010", 10, "desc"));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Account.class));
        assertThat(query.getValue().getQueryObject().toJson()).contains("$lt");
        assertThat(query.getValue().getSortObject()).isEqualTo(new Document("id", -1));
    }

    @Test
    void afterIdWithoutCursorModeIsRejected() {
        AccountQuery query = new AccountQuery(null, "64b000000000000000000010", 1, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.getAccounts(query))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void pageModeClampsPageAndLimit() {
        when(mongoTemplate.count(any(Query.class), eq(Account.class))).thenReturn(250L);
        when(mongoTemplate.find(any(Query.class), eq(Account.class))).thenReturn(List.of());

        AccountPaginationResponse response = service.getAccounts(
                new AccountQuery("page", null, 0, 1000, null, null, null, null, null, null));

        assertThat(response.pagination().page()).isEqualTo(1);
        assertThat(response.pagination().limit()).isEqualTo(100);
        assertThat(response.pagination().totalPages()).isEqualTo(3);
    }

    @Test
    void searchMatchesDepartmentAndFullName() {
        when(mongoTemplate.find(any(Query.class), eq(Account.class))).thenReturn(List.of());

        service.getAccounts(new AccountQuery(null, null, null, null, "Jane Doe", null, null, null, null, null));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Account.class));
        String json = query.getValue().getQueryObject().toJson();
        assertThat(json).contains("department").contains("$regexMatch");
    }

    @Test
    void unsupportedSortFieldIsRejected() {
        assertThatThrownBy(() -> service.getAccounts(
                new AccountQuery(null, null, null, null, null, "passwordHash", null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
