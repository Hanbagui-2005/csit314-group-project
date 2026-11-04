package com.csit314.platform;

import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// end-to-end render checks for this round's ADM-08/ADM-10/ADM-12/IDR-03 fixes - the unit
// tests cover the logic, this proves the actual pages render without a Thymeleaf error
// and that the pieces (controller, service, template) are actually wired together
@SpringBootTest
@AutoConfigureMockMvc
class StoryGapFixesFlowTests {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository userAccountRepository;

    private MockHttpSession loginAdmin() throws Exception {
        return (MockHttpSession) mvc.perform(post("/admin/login").with(csrf())
                        .param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD))
                .andExpect(redirectedUrl("/admin/users"))
                .andReturn().getRequest().getSession(false);
    }

    private void registerDesigner(String email, String company) throws Exception {
        mvc.perform(post("/designer/register").with(csrf())
                        .param("contactName", "Jordan Lee")
                        .param("email", email)
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("companyName", company)
                        .param("companyDescription", "Modern kitchens")
                        .param("companyPhone", "+61 400 000 000"))
                .andExpect(redirectedUrl("/designer/login"));
    }

    // ADM-08
    @Test
    void usersPageHonoursTheStatusAndRoleFilters() throws Exception {
        registerDesigner("pending.filter@studio.com", "Pending Filter Studio");
        MockHttpSession admin = loginAdmin();

        mvc.perform(get("/admin/users").session(admin)
                        .param("status", "Pending").param("role", "Interior Designer"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pending Filter Studio")))
                .andExpect(content().string(containsString("pending.filter@studio.com")));

        mvc.perform(get("/admin/users").session(admin)
                        .param("status", "Suspended").param("role", "Customer"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("pending.filter@studio.com"))));
    }

    // ADM-10
    @Test
    void complaintsPageRendersWithAndWithoutTheDesignerFilter() throws Exception {
        MockHttpSession admin = loginAdmin();

        mvc.perform(get("/admin/complaints").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/complaints").session(admin).param("designerId", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No complaints filed against this designer")));
    }

    @Test
    void qualityPageRenders() throws Exception {
        MockHttpSession admin = loginAdmin();

        mvc.perform(get("/admin/quality").session(admin)).andExpect(status().isOk());
    }

    // ADM-12 - the pending count shows up in the navbar on any admin page, not just /admin/users
    @Test
    void pendingRegistrationCountShowsInTheNavbarOnAnyAdminPage() throws Exception {
        registerDesigner("navbar.badge@studio.com", "Navbar Badge Studio");
        MockHttpSession admin = loginAdmin();

        mvc.perform(get("/admin/quality").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pending")));
    }

    // IDR-03
    @Test
    void designerAnalyticsPageRendersTheEngagementBreakdown() throws Exception {
        registerDesigner("analytics.page@studio.com", "Analytics Page Studio");
        InteriorDesigner designer = (InteriorDesigner) userAccountRepository
                .findByEmail("analytics.page@studio.com").orElseThrow();

        MockHttpSession admin = loginAdmin();
        mvc.perform(post("/admin/users/" + designer.getId() + "/approve").with(csrf()).session(admin))
                .andExpect(redirectedUrl("/admin/users"));

        MockHttpSession session = (MockHttpSession) mvc.perform(post("/designer/login").with(csrf())
                        .param("email", "analytics.page@studio.com").param("password", "password123"))
                .andExpect(redirectedUrl("/designer/dashboard"))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/designer/analytics").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ranked by engagement")));
    }
}
