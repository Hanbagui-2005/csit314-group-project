package com.csit314.platform;

import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.InteriorDesignerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

// PM-05 - Platform Management verifying a designer's declared service area
@SpringBootTest
@AutoConfigureMockMvc
class PlatformManagerDesignersFlowTests {

    private static final String PM_EMAIL = "platform-manager@test.local";
    private static final String PM_PASSWORD = "Manager@12345";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InteriorDesignerRepository designerRepository;

    @Test
    void designersPageRequiresPlatformManagerLogin() throws Exception {
        mvc.perform(get("/platform-manager/designers")).andExpect(redirectedUrl("/platform-manager/login"));
    }

    @Test
    void listsDesignersWithTheirDeclaredServiceAreaAndVerificationStatus() throws Exception {
        InteriorDesigner designer = newDesigner("area.listing@test.local", "Area Listing Studio", "Sydney Metro, NSW");

        MockHttpSession session = loginPlatformManager();
        mvc.perform(get("/platform-manager/designers").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Area Listing Studio")))
                .andExpect(content().string(containsString("Sydney Metro, NSW")))
                .andExpect(content().string(containsString("Unverified")));

        assertThat(designerRepository.findById(designer.getId()).orElseThrow().isServiceAreaVerified()).isFalse();
    }

    @Test
    void verifyingThenFlaggingTogglesServiceAreaVerified() throws Exception {
        InteriorDesigner designer = newDesigner("area.toggle@test.local", "Area Toggle Studio", "Melbourne CBD");
        MockHttpSession session = loginPlatformManager();

        mvc.perform(post("/platform-manager/designers/" + designer.getId() + "/verify-service-area").with(csrf())
                        .session(session))
                .andExpect(redirectedUrl("/platform-manager/designers"))
                .andExpect(flash().attribute("successMessage", "Service area marked as verified."));
        assertThat(designerRepository.findById(designer.getId()).orElseThrow().isServiceAreaVerified()).isTrue();

        mvc.perform(post("/platform-manager/designers/" + designer.getId() + "/flag-service-area").with(csrf())
                        .session(session))
                .andExpect(flash().attribute("successMessage", "Service area flagged as inaccurate."));
        assertThat(designerRepository.findById(designer.getId()).orElseThrow().isServiceAreaVerified()).isFalse();
    }

    private InteriorDesigner newDesigner(String email, String studioName, String serviceArea) {
        Company company = new Company(studioName, "Testing only", "+61 400 000 002");
        company.setServiceArea(serviceArea);
        return designerRepository.save(new InteriorDesigner("Designer", email, "encoded", company));
    }

    private MockHttpSession loginPlatformManager() throws Exception {
        return (MockHttpSession) mvc.perform(post("/platform-manager/login").with(csrf())
                        .param("email", PM_EMAIL).param("password", PM_PASSWORD))
                .andExpect(redirectedUrl("/platform-manager/categories"))
                .andReturn().getRequest().getSession(false);
    }
}
