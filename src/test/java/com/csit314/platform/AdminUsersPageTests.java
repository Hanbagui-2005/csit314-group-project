package com.csit314.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUsersPageTests {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Autowired
    private MockMvc mvc;

    @Test
    void theBulkFormDoesNotNestTheRowFormsInsideIt() throws Exception {
        MockHttpSession session = loginAdmin();

        String html = mvc.perform(get("/admin/users").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Pattern pattern = Pattern.compile(
                "<form\\b[^>]*\\bid=\"bulkForm\"[^>]*>(.*?)</form>",
                Pattern.DOTALL
        );

        Matcher bulkForm = pattern.matcher(html);

        assertThat(bulkForm.find())
                .as("The bulk-action form must exist")
                .isTrue();

        assertThat(bulkForm.group(1))
                .doesNotContain("<form")
                .contains("type=\"hidden\"")
                .contains("name=\"_csrf\"");
    }

    private MockHttpSession loginAdmin() throws Exception {
        return (MockHttpSession) mvc.perform(post("/admin/login")
                        .with(csrf())
                        .param("email", ADMIN_EMAIL)
                        .param("password", ADMIN_PASSWORD))
                .andExpect(redirectedUrl("/admin/users"))
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
