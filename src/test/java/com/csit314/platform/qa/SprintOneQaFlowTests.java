package com.csit314.platform.qa;

import com.csit314.platform.model.*;
import com.csit314.platform.repository.*;
import com.csit314.platform.service.*;
import com.csit314.platform.web.dto.*;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP, session and H2 persistence checks supplement the isolated service tests. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:qa_flows;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class SprintOneQaFlowTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired InteriorDesignerRepository designers;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired CustomerService customerService;
    @Autowired DesignerService designerService;
    @Autowired PasswordResetService passwordResetService;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;

    private static final String PASSWORD = "QaPassword!123";

    private Customer customer(String email) {
        return customerService.register(new CustomerRegistrationForm(
                "QA Customer", email, PASSWORD, PASSWORD));
    }

    private InteriorDesigner designer(String email) {
        return designerService.register(new DesignerRegistrationForm(
                "QA Contact", email, PASSWORD, PASSWORD, "QA Studio",
                "Synthetic company for automated tests.", "5550000000"));
    }

    private MockHttpSession adminSession() throws Exception {
        return login("/admin/login", "admin@test.local", "Admin@12345", "/admin/users");
    }

    private MockHttpSession login(String path, String email, String password, String redirect) throws Exception {
        return (MockHttpSession) mvc.perform(post(path).param("email", email).param("password", password))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(redirect))
                .andReturn().getRequest().getSession(false);
    }

    private void assertNotAuthenticated(MvcResult result) {
        var session = result.getRequest().getSession(false);
        assertThat(session == null || session.getAttribute(LoginSession.CURRENT_USER) == null).isTrue();
    }

    @Test
    @DisplayName("#75 Admin login creates an admin session and records the login time")
    void adminLoginCreatesSessionAndRecordsLogin() throws Exception {
        MockHttpSession session = adminSession();
        SessionUser snapshot = (SessionUser) session.getAttribute(LoginSession.CURRENT_USER);
        assertThat(snapshot.role()).isEqualTo(Role.ADMIN);
        assertThat(snapshot.email()).isEqualTo("admin@test.local");
        assertThat(users.findByEmail(snapshot.email()).orElseThrow().getLastLoginAt()).isNotNull();
        mvc.perform(get("/admin/users").session(session)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-password", "", "   "})
    @DisplayName("#75 Invalid admin credentials do not create an authenticated session")
    void invalidAdminPasswordDoesNotAuthenticate(String password) throws Exception {
        MvcResult result = mvc.perform(post("/admin/login")
                        .param("email", "admin@test.local").param("password", password))
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attribute("errorMessage", "Invalid email or password."))
                .andReturn();
        assertNotAuthenticated(result);
    }

    @Test
    @DisplayName("#95 Persisted user list includes every role, displays safe summaries and sorts newest first")
    void userListIncludesAllRolesWithSafeSortedSummaries() throws Exception {
        Customer older = customer("qa.list.older@example.test");
        InteriorDesigner newer = designer("qa.list.newer@example.test");
        ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.of(2030, 1, 1, 12, 0));
        ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.of(2030, 1, 2, 12, 0));
        users.flush();
        entityManager.clear();
        MvcResult result = mvc.perform(get("/admin/users").session(adminSession()))
                .andExpect(status().isOk()).andExpect(view().name("admin-users"))
                .andExpect(model().attributeExists("users")).andReturn();
        Object modelUsers = result.getModelAndView().getModel().get("users");
        assertThat(modelUsers).isInstanceOf(List.class);
        List<?> summaries = (List<?>) modelUsers;
        assertThat(summaries).hasSize((int) users.count());
        List<UserSummary> fixtures = summaries.stream().map(UserSummary.class::cast)
                .filter(row -> row.email().startsWith("qa.list.")).toList();
        assertThat(fixtures).extracting(UserSummary::email)
                .containsExactly(newer.getEmail(), older.getEmail());
        assertThat(fixtures.getFirst().id()).isEqualTo(newer.getId());
        assertThat(fixtures.getFirst().name()).isEqualTo("QA Studio");
        assertThat(fixtures.getFirst().role()).isEqualTo("Interior Designer");
        assertThat(fixtures.getFirst().status()).isEqualTo("Pending");
        assertThat(result.getResponse().getContentAsString())
                .contains("admin@test.local", newer.getEmail(), older.getEmail())
                .doesNotContain(PASSWORD, newer.getPasswordHash(), older.getPasswordHash());
    }

    @Test
    @DisplayName("#95 Anonymous requests cannot retrieve the admin user list")
    void anonymousUserCannotReadUserList() throws Exception {
        mvc.perform(get("/admin/users")).andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @DisplayName("#99 Approval persists ACTIVE and enables designer authentication")
    void approvalPersistsActiveAndAllowsDesignerLogin() throws Exception {
        InteriorDesigner designer = designer("qa.approve@example.test");
        Long id = designer.getId();
        mvc.perform(post("/admin/users/" + id + "/approve").session(adminSession()))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("successMessage"));
        users.flush();
        entityManager.clear();
        InteriorDesigner saved = designers.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getRejectionReason()).isNull();
        MockHttpSession session = login("/designer/login", saved.getEmail(), PASSWORD, "/designer/dashboard");
        mvc.perform(get("/designer/dashboard").session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("#99/#103 Customers cannot approve or reject registrations")
    void customerCannotReviewDesignerRegistration() throws Exception {
        InteriorDesigner designer = designer("qa.review@example.test");
        Customer customer = customer("qa.reviewer@example.test");
        MockHttpSession session = login("/login", customer.getEmail(), PASSWORD, "/favourites");
        mvc.perform(post("/admin/users/" + designer.getId() + "/approve").session(session))
                .andExpect(redirectedUrl("/admin/login"));
        mvc.perform(post("/admin/users/" + designer.getId() + "/reject")
                        .session(session).param("reason", "Not allowed"))
                .andExpect(redirectedUrl("/admin/login"));
        assertThat(designer.getStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(designer.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("#103 Rejection persists REJECTED and the trimmed reason, and prevents login")
    void rejectionIsPersistedAndPreventsLogin() throws Exception {
        InteriorDesigner designer = designer("qa.reject@example.test");
        Long id = designer.getId();
        mvc.perform(post("/admin/users/" + id + "/reject").session(adminSession())
                        .param("reason", "  Missing company information  "))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("successMessage"));
        users.flush();
        entityManager.clear();
        InteriorDesigner saved = designers.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.REJECTED);
        assertThat(saved.getRejectionReason()).isEqualTo("Missing company information");
        assertThat(saved.getReviewedAt()).isNotNull();
        MvcResult result = mvc.perform(post("/designer/login").param("email", saved.getEmail())
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/designer/login"))
                .andExpect(flash().attribute("errorMessage", containsString(saved.getRejectionReason())))
                .andReturn();
        assertNotAuthenticated(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("#103 Rejection without a reason leaves the registration PENDING")
    void rejectionRequiresReason(String reason) throws Exception {
        InteriorDesigner designer = designer("qa.reject.blank@example.test");
        var request = post("/admin/users/" + designer.getId() + "/reject").session(adminSession());
        if (reason != null) request.param("reason", reason);
        mvc.perform(request).andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertThat(designer.getStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(designer.getReviewedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    @DisplayName("#111 Non-active designers cannot create a login session")
    void nonActiveDesignerCannotCreateSession(AccountStatus status) throws Exception {
        InteriorDesigner designer = designer("qa.status@example.test");
        designer.setStatus(status);
        MvcResult result = mvc.perform(post("/designer/login").param("email", designer.getEmail())
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/designer/login"))
                .andExpect(flash().attributeExists("errorMessage")).andReturn();
        assertNotAuthenticated(result);
        assertThat(designer.getLastLoginAt()).isNull();
    }

    @Test
    @DisplayName("#111 An ACTIVE designer session has the correct identity and role")
    void designerSessionUsesCorrectIdentityAndRole() throws Exception {
        InteriorDesigner designer = designer("qa.active@example.test");
        designer.approve();
        MockHttpSession session = login("/designer/login", designer.getEmail(), PASSWORD, "/designer/dashboard");
        SessionUser snapshot = (SessionUser) session.getAttribute(LoginSession.CURRENT_USER);
        assertThat(snapshot.id()).isEqualTo(designer.getId());
        assertThat(snapshot.role()).isEqualTo(Role.INTERIOR_DESIGNER);
        assertThat(snapshot.name()).isEqualTo(designer.getDisplayName());
        mvc.perform(get("/admin/users").session(session)).andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @DisplayName("#119 Customer login rotates the session ID, enforces role and supports logout")
    void customerSessionRotatesAndLogoutRemovesAccess() throws Exception {
        Customer customer = customer("qa.session@example.test");
        MockHttpSession existing = new MockHttpSession();
        String previousId = existing.getId();
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").session(existing)
                        .param("email", customer.getEmail()).param("password", PASSWORD))
                .andExpect(redirectedUrl("/favourites"))
                .andReturn().getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(previousId);
        SessionUser snapshot = (SessionUser) session.getAttribute(LoginSession.CURRENT_USER);
        assertThat(snapshot.id()).isEqualTo(customer.getId());
        assertThat(snapshot.role()).isEqualTo(Role.CUSTOMER);
        mvc.perform(get("/favourites").session(session)).andExpect(status().isOk());
        mvc.perform(get("/admin/users").session(session)).andExpect(redirectedUrl("/admin/login"));
        mvc.perform(post("/logout").session(session)).andExpect(redirectedUrl("/search"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/favourites")).andExpect(redirectedUrl("/search"));
    }

    @Test
    @DisplayName("#119 Wrong customer password does not create an authenticated session")
    void wrongCustomerPasswordDoesNotCreateSession() throws Exception {
        Customer customer = customer("qa.session.failure@example.test");
        MvcResult result = mvc.perform(post("/login").param("email", customer.getEmail()).param("password", "wrong"))
                .andExpect(redirectedUrl("/search"))
                .andExpect(flash().attribute("errorMessage", "Invalid email or password."))
                .andReturn();
        assertNotAuthenticated(result);
    }

    @Test
    @DisplayName("#123 Reset requests return the same public response for known and unknown accounts")
    void resetRequestDoesNotDiscloseAccountExistence() throws Exception {
        Customer customer = customer("qa.reset.request@example.test");
        MvcResult known = mvc.perform(post("/forgot-password").param("email", customer.getEmail()))
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attributeExists("successMessage")).andReturn();
        MvcResult unknown = mvc.perform(post("/forgot-password").param("email", "qa.missing@example.test"))
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attributeExists("successMessage")).andReturn();
        assertThat(unknown.getFlashMap().get("successMessage"))
                .isEqualTo(known.getFlashMap().get("successMessage"));
    }

    @Test
    @DisplayName("#123 Password reset persists a new hash, invalidates the old password and consumes the token")
    void resetPersistsPasswordAndConsumesToken() throws Exception {
        Customer customer = customer("qa.reset.success@example.test");
        Long id = customer.getId();
        String raw = passwordResetService.requestReset(customer.getEmail()).orElseThrow();
        String newPassword = "NewQaPassword!456";
        mvc.perform(post("/reset-password").param("token", raw).param("password", newPassword)
                        .param("confirmPassword", newPassword))
                .andExpect(redirectedUrl("/search"));
        users.flush();
        entityManager.clear();
        UserAccount saved = users.findById(id).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(newPassword);
        assertThat(encoder.matches(newPassword, saved.getPasswordHash())).isTrue();
        assertThat(encoder.matches(PASSWORD, saved.getPasswordHash())).isFalse();
        assertThat(passwordResetService.isTokenValid(raw)).isFalse();
        mvc.perform(get("/reset-password").param("token", raw))
                .andExpect(redirectedUrl("/forgot-password"));
        login("/login", saved.getEmail(), newPassword, "/favourites");
        mvc.perform(post("/login").param("email", saved.getEmail()).param("password", PASSWORD))
                .andExpect(flash().attribute("errorMessage", "Invalid email or password."));
    }

    @Test
    @DisplayName("#123 A replacement reset request invalidates the previous link")
    void replacementRequestInvalidatesOldToken() {
        Customer customer = customer("qa.reset.replace@example.test");
        String oldToken = passwordResetService.requestReset(customer.getEmail()).orElseThrow();
        String newToken = passwordResetService.requestReset(customer.getEmail()).orElseThrow();
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(passwordResetService.isTokenValid(oldToken)).isFalse();
        assertThat(passwordResetService.isTokenValid(newToken)).isTrue();
    }

    @Test
    @DisplayName("#123 Mismatched passwords preserve the existing hash and leave the token usable")
    void mismatchPreservesPasswordAndToken() throws Exception {
        Customer customer = customer("qa.reset.mismatch@example.test");
        String oldHash = customer.getPasswordHash();
        String raw = passwordResetService.requestReset(customer.getEmail()).orElseThrow();
        mvc.perform(post("/reset-password").param("token", raw)
                        .param("password", "NewQaPassword!456").param("confirmPassword", "DifferentQaPassword!456"))
                .andExpect(redirectedUrl("/reset-password?token=" + raw))
                .andExpect(flash().attribute("errorMessage", "Passwords do not match."));
        assertThat(customer.getPasswordHash()).isEqualTo(oldHash);
        assertThat(passwordResetService.isTokenValid(raw)).isTrue();
    }

    @Test
    @DisplayName("#123 An expired reset token cannot modify the persisted account password")
    void expiredTokenCannotChangePassword() throws Exception {
        Customer customer = customer("qa.reset.expired@example.test");
        String originalHash = customer.getPasswordHash();
        String raw = passwordResetService.requestReset(customer.getEmail()).orElseThrow();
        PasswordResetToken token = tokens.findByUserAndUsedAtIsNull(customer).getFirst();
        ReflectionTestUtils.setField(token, "expiresAt", LocalDateTime.now().minusMinutes(1));
        tokens.flush();
        entityManager.clear();
        mvc.perform(post("/reset-password").param("token", raw)
                        .param("password", "NewQaPassword!456").param("confirmPassword", "NewQaPassword!456"))
                .andExpect(flash().attribute("errorMessage", containsString("invalid or has expired")));
        assertThat(users.findById(customer.getId()).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
        assertThat(passwordResetService.isTokenValid(raw)).isFalse();
    }
}
