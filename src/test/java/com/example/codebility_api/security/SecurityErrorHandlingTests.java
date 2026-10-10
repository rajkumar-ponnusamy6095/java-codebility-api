package com.example.codebility_api.security;

import com.example.codebility_api.dto.LoginResponse;
import com.example.codebility_api.service.AccountService;
import com.example.codebility_api.service.JwtService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private static final String SERVLET_PATH = "/api/v1";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private AccountService accountService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter springSecurityFilterChain =
                context.getBean("springSecurityFilterChain", Filter.class);

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    private static MockHttpServletRequestBuilder api(
            MockHttpServletRequestBuilder builder) {
        return builder.servletPath(SERVLET_PATH);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void missingTokenOnProtectedEndpointReturns401() throws Exception {
        mockMvc.perform(api(get(SERVLET_PATH + "/accounts")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value(SERVLET_PATH + "/accounts"));
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(api(get(SERVLET_PATH + "/accounts/me"))
                        .header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired access token"));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String expired = new JwtService(jwtSecret, -60_000)
                .generateToken("user@example.com", "User");

        mockMvc.perform(api(get(SERVLET_PATH + "/accounts/me"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired access token"));
    }

    @Test
    void tokenWithInvalidRoleReturns401() throws Exception {
        String token = jwtService.generateToken("user@example.com", "Guest");

        mockMvc.perform(api(get(SERVLET_PATH + "/accounts/me"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid access token"));
    }

    @Test
    void userRoleOnAdminEndpointReturns403() throws Exception {
        String token = jwtService.generateToken("user@example.com", "User");

        mockMvc.perform(api(get(SERVLET_PATH + "/accounts"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("Insufficient role to access this resource"));
    }

    @Test
    void adminCanReadAccountAndResponseIncludesGender() throws Exception {
        when(accountService.getAccountById("abc123")).thenReturn(
                new LoginResponse.AccountDetails(
                        "abc123", "jane@example.com", "Jane", "Doe",
                        "Female", "User", "active", true
                ));
        String token = jwtService.generateToken("admin@example.com", "Admin");

        mockMvc.perform(api(get(SERVLET_PATH + "/accounts/abc123"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("Female"));
    }

    @Test
    void updateWithBlankGenderReturns400WithFieldError() throws Exception {
        String token = jwtService.generateToken("admin@example.com", "Admin");
        String body = """
                {
                  "email": "jane@example.com",
                  "firstName": "Jane",
                  "lastName": "Doe",
                  "gender": "   ",
                  "role": "User",
                  "status": "active"
                }
                """;

        mockMvc.perform(api(put(SERVLET_PATH + "/accounts/abc123"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.gender").value("Gender is required"));

        verify(accountService, never()).updateAccount(anyString(), any());
    }

    @Test
    void responseStatusExceptionOnPublicEndpointKeepsItsStatus() throws Exception {
        when(accountService.authenticate(any())).thenThrow(
                new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"
                ));

        mockMvc.perform(api(post(SERVLET_PATH + "/accounts/authenticate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "jane@example.com", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
