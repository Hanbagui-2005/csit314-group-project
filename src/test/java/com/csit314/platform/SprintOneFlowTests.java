package com.csit314.platform;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.service.PasswordResetService;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SprintOneFlowTests {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetService passwordResetService;

    @Test
    void adminCanLogInAndSeeAllUsers() throws Exception {
        registerCustomer("Listed Person", "listed@example.com", "password123");

        MockHttpSession session = login("/admin/login", ADMIN_EMAIL, ADMIN_PASSWORD, "/admin/users");
        assertThat(sessionUser(session).role().name()).isEqualTo("ADMIN");

        mvc.perform(get("/admin/users").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("listed@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(ADMIN_EMAIL)));
    }

    @Test
    void adminPasswordIsStoredHashed() {
        UserAccount admin = userAccountRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getPasswordHash()).isNotEqualTo(ADMIN_PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
    }

    @Test
    void wrongPasswordAndWrongPortalAreRejected() throws Exception {
        mvc.perform(post("/admin/login").param("email", ADMIN_EMAIL).param("password", "nope"))
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attribute("errorMessage", "Invalid email or password."));

        // admin credentials don't work on the customer login
        mvc.perform(post("/login").param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD))
                .andExpect(redirectedUrl("/search"))
                .andExpect(flash().attribute("errorMessage", "Invalid email or password."));
    }

    @Test
    void adminPagesRequireAdminLogin() throws Exception {
        mvc.perform(get("/admin/users")).andExpect(redirectedUrl("/admin/login"));
        mvc.perform(get("/designer/dashboard")).andExpect(redirectedUrl("/designer/login"));
        mvc.perform(get("/favourites")).andExpect(redirectedUrl("/search"));
    }

    @Test
    void designerRegistersThenLogsInOnlyAfterApproval() throws Exception {
        registerDesigner("approve.me@studio.com", "Approve Studio");

        InteriorDesigner designer = (InteriorDesigner) userAccountRepository.findByEmail("approve.me@studio.com").orElseThrow();
        assertThat(designer.getStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(designer.getCompany().getName()).isEqualTo("Approve Studio");
        assertThat(designer.getCompany().getPhone()).isEqualTo("+61 400 000 000");

        mvc.perform(post("/designer/login").param("email", "approve.me@studio.com").param("password", "password123"))
                .andExpect(redirectedUrl("/designer/login"))
                .andExpect(flash().attribute("errorMessage",
                        org.hamcrest.Matchers.containsString("still being reviewed")));

        MockHttpSession admin = login("/admin/login", ADMIN_EMAIL, ADMIN_PASSWORD, "/admin/users");
        mvc.perform(post("/admin/users/" + designer.getId() + "/approve").session(admin))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("successMessage"));

        assertThat(userAccountRepository.findById(designer.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);

        MockHttpSession session = login("/designer/login", "approve.me@studio.com", "password123", "/designer/dashboard");
        mvc.perform(get("/designer/dashboard").session(session)).andExpect(status().isOk());
    }

    @Test
    void rejectedDesignerSeesReasonAndCannotLogIn() throws Exception {
        registerDesigner("reject.me@studio.com", "Reject Studio");
        Long id = userAccountRepository.findByEmail("reject.me@studio.com").orElseThrow().getId();
        MockHttpSession admin = login("/admin/login", ADMIN_EMAIL, ADMIN_PASSWORD, "/admin/users");

        // a reason is required
        mvc.perform(post("/admin/users/" + id + "/reject").session(admin).param("reason", " "))
                .andExpect(flash().attributeExists("errorMessage"));

        mvc.perform(post("/admin/users/" + id + "/reject").session(admin).param("reason", "No portfolio provided"))
                .andExpect(flash().attributeExists("successMessage"));

        InteriorDesigner designer = (InteriorDesigner) userAccountRepository.findById(id).orElseThrow();
        assertThat(designer.getStatus()).isEqualTo(AccountStatus.REJECTED);
        assertThat(designer.getRejectionReason()).isEqualTo("No portfolio provided");

        mvc.perform(post("/designer/login").param("email", "reject.me@studio.com").param("password", "password123"))
                .andExpect(flash().attribute("errorMessage",
                        org.hamcrest.Matchers.containsString("No portfolio provided")));

        // can't review the same registration twice
        mvc.perform(post("/admin/users/" + id + "/approve").session(admin))
                .andExpect(flash().attribute("errorMessage", "This registration has already been reviewed."));
    }

    @Test
    void customerRegistersAndLogsIn() throws Exception {
        registerCustomer("Jordan Lee", "Jordan.Lee@Example.com", "password123");

        UserAccount saved = userAccountRepository.findByEmail("jordan.lee@example.com").orElseThrow();
        assertThat(saved).isInstanceOf(Customer.class);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        MockHttpSession session = login("/login", "jordan.lee@example.com", "password123", "/favourites");
        mvc.perform(get("/favourites").session(session)).andExpect(status().isOk());

        mvc.perform(post("/logout").session(session)).andExpect(redirectedUrl("/search"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void customerRegistrationValidatesInput() throws Exception {
        registerCustomer("Dup", "dup@example.com", "password123");

        mvc.perform(post("/register").param("name", "Dup Again").param("email", "dup@example.com")
                        .param("password", "password123").param("confirmPassword", "password123"))
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("errorMessage", "An account with this email already exists."));

        mvc.perform(post("/register").param("name", "Short").param("email", "short@example.com")
                        .param("password", "abc").param("confirmPassword", "abc"))
                .andExpect(flash().attribute("errorMessage", "Password must be at least 8 characters."));

        mvc.perform(post("/register").param("name", "Mismatch").param("email", "mismatch@example.com")
                        .param("password", "password123").param("confirmPassword", "password124"))
                .andExpect(flash().attribute("errorMessage", "Passwords do not match."));
    }

    @Test
    void customerCanResetForgottenPassword() throws Exception {
        registerCustomer("Forgetful", "forgetful@example.com", "oldpassword");

        // unknown emails get the same response
        mvc.perform(post("/forgot-password").param("email", "nobody@example.com"))
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attributeExists("successMessage"));

        String token = passwordResetService.requestReset("forgetful@example.com").orElseThrow();

        mvc.perform(get("/reset-password").param("token", token)).andExpect(status().isOk());
        mvc.perform(post("/reset-password").param("token", token)
                        .param("password", "newpassword").param("confirmPassword", "newpassword"))
                .andExpect(redirectedUrl("/search"));

        login("/login", "forgetful@example.com", "newpassword", "/favourites");
        mvc.perform(post("/login").param("email", "forgetful@example.com").param("password", "oldpassword"))
                .andExpect(flash().attribute("errorMessage", "Invalid email or password."));

        // link is single-use
        mvc.perform(get("/reset-password").param("token", token)).andExpect(redirectedUrl("/forgot-password"));
    }

    private void registerCustomer(String name, String email, String password) throws Exception {
        mvc.perform(post("/register").param("name", name).param("email", email)
                        .param("password", password).param("confirmPassword", password))
                .andExpect(redirectedUrl("/search"));
    }

    private void registerDesigner(String email, String company) throws Exception {
        mvc.perform(post("/designer/register")
                        .param("contactName", "Jordan Lee")
                        .param("email", email)
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("companyName", company)
                        .param("companyDescription", "Modern kitchens")
                        .param("companyPhone", "+61 400 000 000"))
                .andExpect(redirectedUrl("/designer/login"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    private MockHttpSession login(String path, String email, String password, String expectedRedirect) throws Exception {
        return (MockHttpSession) mvc.perform(post(path).param("email", email).param("password", password))
                .andExpect(redirectedUrl(expectedRedirect))
                .andReturn().getRequest().getSession(false);
    }

    private static SessionUser sessionUser(MockHttpSession session) {
        return (SessionUser) session.getAttribute(LoginSession.CURRENT_USER);
    }
}
