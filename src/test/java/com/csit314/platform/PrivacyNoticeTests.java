package com.csit314.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PrivacyNoticeTests {
    @Autowired private MockMvc mvc;

    @Test
    void visitorsCanReadDataUseWithoutSigningIn() throws Exception {
        mvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(view().name("privacy"))
                .andExpect(content().string(containsString("Privacy and data use")))
                .andExpect(content().string(containsString("Account information")))
                .andExpect(content().string(containsString("Planned analytics")))
                .andExpect(content().string(containsString("not implemented")));
    }

    @Test
    void browsePageLinksToNoticeBeforeRegistration() throws Exception {
        mvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/privacy\"")));
    }
}
