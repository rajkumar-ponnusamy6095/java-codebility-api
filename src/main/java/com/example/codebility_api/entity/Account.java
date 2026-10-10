
package com.example.codebility_api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "accounts")
public class Account {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    @JsonIgnore
    private String passwordHash;

    private String phone;
    private String department;
    private String status = "active";
    private String gender;
    private String firstName;
    private String lastName;
    private Boolean acceptTerms;
    private String role;

    @JsonIgnore
    private String verificationToken;

    private Instant verified;

    private ResetToken resetToken;

    private Instant passwordReset;
    private Instant created;

    private Instant createdAt = Instant.now();
    private Instant updated;

    public Account() {
    }

    public void normalizeEmail() {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
    }

    public boolean isVerified() {
        return verified != null || passwordReset != null;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Boolean getAcceptTerms() { return acceptTerms; }
    public void setAcceptTerms(Boolean acceptTerms) {
        this.acceptTerms = acceptTerms;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    @JsonIgnore
    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public Instant getVerified() { return verified; }
    public void setVerified(Instant verified) {
        this.verified = verified;
    }

    @JsonIgnore
    public ResetToken getResetToken() { return resetToken; }

    public void setResetToken(ResetToken resetToken) {
        this.resetToken = resetToken;
    }

    public Instant getPasswordReset() { return passwordReset; }
    public void setPasswordReset(Instant passwordReset) {
        this.passwordReset = passwordReset;
    }

    @JsonIgnore
    public Instant getCreated() { return created; }

    public void setCreated(Instant created) { this.created = created; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdated() { return updated; }
    public void setUpdated(Instant updated) { this.updated = updated; }

    public static class ResetToken {

        private String token;
        private Instant expires;

        public ResetToken() {
        }

        public ResetToken(String token, Instant expires) {
            this.token = token;
            this.expires = expires;
        }

        @JsonIgnore
        public String getToken() { return token; }

        public void setToken(String token) { this.token = token; }

        @JsonIgnore
        public Instant getExpires() { return expires; }

        public void setExpires(Instant expires) {
            this.expires = expires;
        }
    }
}
