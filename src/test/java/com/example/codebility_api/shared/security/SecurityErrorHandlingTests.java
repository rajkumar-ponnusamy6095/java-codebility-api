package com.example.codebility_api.shared.security;

import com.example.codebility_api.modules.account.dto.AccountProfileResponse;
import com.example.codebility_api.modules.account.service.AccountQueryService;
import com.example.codebility_api.modules.account.service.AccountService;
import com.example.codebility_api.modules.account.service.AuthService;
import com.example.codebility_api.modules.account.service.PasswordService;
import com.example.codebility_api.modules.account.service.RegistrationService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SecurityErrorHandlingTests {

    private static final String API = "/api/v1";
    private static final String USER_ID = "64b000000000000000000001";
    private static final String ADMIN_ID = "64b000000000000000000002";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtService jwtService;
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private AuthenticatedAccountResolver accountResolver;
    @MockitoBean
    private AccountService accountService;
    @MockitoBean
    private AccountQueryService accountQueryService;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private RegistrationService registrationService;
    @MockitoBean
    private PasswordService passwordService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter securityFilterChain = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
        when(accountResolver.resolve(USER_ID)).thenReturn(
                Optional.of(new AuthenticatedAccount(USER_ID, "user@example.com", "User")));
        when(accountResolver.resolve(ADMIN_ID)).thenReturn(
                Optional.of(new AuthenticatedAccount(ADMIN_ID, "admin@example.com", "Admin")));
    }

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath(API);
    }

    private String bearer(String accountId) {
        return "Bearer " + jwtService.generateToken(accountId, "User");
    }

    @Test
    void missingTokenOnProtectedEndpointReturns401() throws Exception {
        mockMvc.perform(api(get(API + "/accounts")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value(API + "/accounts"));
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(api(get(API + "/accounts/me")).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token"));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String expired = new JwtService(jwtSecret, -60_000).generateToken(USER_ID, "User");
        mockMvc.perform(api(get(API + "/accounts/me")).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token"));
    }

    @Test
    void tokenForDeletedAccountReturns401() throws Exception {
        String unknownId = "64b0000000000000000000ff";
        when(accountResolver.resolve(unknownId)).thenReturn(Optional.empty());
        mockMvc.perform(api(get(API + "/accounts/me")).header(HttpHeaders.AUTHORIZATION, bearer(unknownId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid access token"));
    }

    @Test
    void accountWithInvalidRoleReturns401() throws Exception {
        String guestId = "64b0000000000000000000aa";
        when(accountResolver.resolve(guestId)).thenReturn(
                Optional.of(new AuthenticatedAccount(guestId, "guest@example.com", "Guest")));
        mockMvc.perform(api(get(API + "/accounts/me")).header(HttpHeaders.AUTHORIZATION, bearer(guestId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid access token"));
    }

    @Test
    void roleIsTakenFromDatabaseNotTokenClaim() throws Exception {
        String forged = jwtService.generateToken(USER_ID, "Admin");
        mockMvc.perform(api(get(API + "/accounts")).header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isForbidden());
    }

    @Test
    void userRoleOnAdminEndpointReturns403() throws Exception {
        mockMvc.perform(api(get(API + "/accounts")).header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Insufficient role to access this resource"));
    }

    @Test
    void userCanReadOwnAccountAndResponseIncludesGender() throws Exception {
        when(accountService.getAccountById(eq(USER_ID), any())).thenReturn(new AccountProfileResponse(
                USER_ID, "Female", "Jane", "Doe", "user@example.com", null, "HR",
                "active", "User", Instant.now(), null, true));
        mockMvc.perform(api(get(API + "/accounts/" + USER_ID)).header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("Female"))
                .andExpect(jsonPath("$.isVerified").value(true));
    }

    @Test
    void ownershipFailureFromServiceReturns403Json() throws Exception {
        when(accountService.getAccountById(eq(ADMIN_ID), any())).thenThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own account"));
        mockMvc.perform(api(get(API + "/accounts/" + ADMIN_ID)).header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only access your own account"));
    }

    @Test
    void updateWithBlankGenderReturns400WithFieldError() throws Exception {
        mockMvc.perform(api(put(API + "/accounts/" + USER_ID))
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Jane\", \"gender\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.gender").value("Gender must not be blank"));
        verify(accountService, never()).updateAccount(anyString(), any(), any());
    }

    @Test
    void updateWithMismatchedPasswordsReturns400() throws Exception {
        mockMvc.perform(api(put(API + "/accounts/" + USER_ID))
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"newpassword1\", \"confirmPassword\": \"different1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.confirmPassword").value("Passwords do not match"));
    }

    @Test
    void registerRequiresMatchingConfirmPassword() throws Exception {
        String body = """
                {"email": "a@example.com", "password": "password123", "confirmPassword": "password124",
                 "firstName": "A", "lastName": "B", "gender": "Male", "acceptTerms": true}
                """;
        mockMvc.perform(api(post(API + "/accounts/register")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.confirmPassword").value("Passwords do not match"));
        verify(registrationService, never()).register(any(), any());
    }

    @Test
    void malformedJsonReturns400Json() throws Exception {
        mockMvc.perform(api(post(API + "/accounts/authenticate")).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request"));
    }

    @Test
    void responseStatusExceptionOnPublicEndpointKeepsItsStatus() throws Exception {
        when(authService.authenticate(any(), any())).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));
        mockMvc.perform(api(post(API + "/accounts/authenticate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"a@example.com\", \"password\": \"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    }

    @Test
    void revokeTokenRequiresAuthentication() throws Exception {
        mockMvc.perform(api(post(API + "/accounts/revoke-token")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
