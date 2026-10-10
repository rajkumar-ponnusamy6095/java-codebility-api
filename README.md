# Codebility API

Spring Boot 4 / Java 21 REST API for account management and authentication, backed by MongoDB. It is the Java counterpart of the Node.js `cinefy` service and exposes the same `/api/v1/accounts` contract.

## Features

- Registration with email verification. The first account becomes `Admin`. Registering an existing email returns the normal success response and emails the owner, so the endpoint does not reveal which emails exist.
- JWT access tokens (15 min, `sub` = account id). On every request the account is reloaded, so role changes and deletions apply immediately.
- Refresh tokens (7 days) in an HttpOnly `refreshToken` cookie, with rotation, revocation and IP tracking. Only SHA-256 hashes are stored, and a TTL index removes expired tokens.
- Forgot/reset/change password. Forgot-password returns the same response whether or not the email exists. Resetting or changing a password revokes all of the account's sessions.
- Admin-created accounts receive a "set your password" email instead of a password.
- Role-based access: Admins can act on any account, Users only on their own.
- Offset (`pagination=page`) and keyset (`pagination=cursor&afterId=`) listing with search and filters. Page size is capped at 100.
- Errors come back as one JSON shape (`timestamp, status, error, message, path, errors`), and every 401/403 is logged with method, path and reason.

## Project structure

The code is organised by feature (module), with a small shared core:

```
com.example.codebility_api
├── CodebilityApiApplication
├── shared/                     cross-cutting infrastructure, no business rules
│   ├── config/                 OpenAPI, password encoder, Mongo connection pool
│   ├── email/                  EmailService (SMTP transport)
│   ├── exception/              ErrorResponse, GlobalExceptionHandler
│   ├── security/               SecurityConfig, JWT filter/service, 401/403 handlers,
│   │                           AuthenticatedAccount principal, SecureTokens
│   ├── validation/             reusable constraints (@FieldsMatch)
│   └── web/                    request logging, client IP resolution
└── modules/                    one package per business capability
    ├── account/
    │   ├── web/                controllers (AuthController, AccountController)
    │   ├── dto/                request/response records
    │   ├── service/            use cases (AuthService, RegistrationService,
    │   │                       PasswordService, AccountService, AccountQueryService, ...)
    │   ├── domain/             Mongo documents (Account, RefreshToken, Role)
    │   └── repository/         Spring Data repositories, index definitions
    └── health/
        └── web/
```

Conventions for adding a module:

1. Create `modules/<name>/{web,dto,service,domain,repository}`. Only add the layers the module needs.
2. Code in `modules/*` may depend on `shared/*`. Code in `shared/*` must not depend on any module. When shared code needs module data, it declares an interface that a module implements, the way `AuthenticatedAccountResolver` is implemented by `account`.
3. Modules talk to each other through their `service` classes, not through each other's repositories or documents.
4. Each module declares the Mongo indexes its queries need (see `AccountIndexInitializer`). Auto index creation is disabled.
5. Throw `ResponseStatusException` for expected failures. `GlobalExceptionHandler` turns them into the shared JSON error format.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `MONGODB_URI` / `DB_CONN` | — | MongoDB connection string (`MONGODB_URI` wins) |
| `DB_MAX_POOL_SIZE` | `100` | Mongo connection pool size |
| `DB_ENSURE_INDEXES` | `true` | Create module indexes at startup |
| `JWT_SECRET` | — | Base64 HMAC key, at least 256 bits |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USER` / `SMTP_PASS` | Ethereal | SMTP settings (`ETHEREAL_USER`/`ETHEREAL_PASS` still accepted) |
| `EMAIL_FROM` | SMTP user | Sender address |
| `APP_URL` | `http://localhost:4310,http://localhost:4311` | Frontend URLs. Email links use the request `Origin` when it is listed here |
| `CORS_ORIGINS` | same as above | Allowed CORS origins (`CORS_ALLOWED_ORIGINS` still accepted) |
| `COOKIE_SECURE` / `COOKIE_SAME_SITE` | `false` / `Lax` | Refresh-token cookie flags |
| `PORT` | `8080` | HTTP port |

## Development

```bash
export JAVA_HOME=/path/to/jdk-21
sh ./gradlew test        # unit + web tests (no MongoDB needed)
sh ./gradlew bootRun     # requires MONGODB_URI and JWT_SECRET
```

Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`.
