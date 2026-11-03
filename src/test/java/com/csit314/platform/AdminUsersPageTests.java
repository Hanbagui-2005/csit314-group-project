package com.csit314.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ADM-08 - admin-users.html used to put a <form> for each row's own actions inside the
// bulk-action <form>, which is invalid HTML and broke the first row's buttons
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

        mvc.perform(get("/admin/users").session(session))
                .andExpect(status().isOk())
                // the bulk form is now empty - nothing else (no row's own <form>) sits between its tags
                .andExpect(content().string(containsString("id=\"bulkForm\"></form>")));
    }

    private MockHttpSession loginAdmin() throws Exception {
        return (MockHttpSession) mvc.perform(post("/admin/login")
                        .param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD))
                .andExpect(redirectedUrl("/admin/users"))
                .andReturn().getRequest().getSession(false);
    }
}
