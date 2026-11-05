package com.csit314.platform;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.service.DesignerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DuplicateLicenseFlowTests {

    private static final String PASSWORD = "Designer!2026";

    @Autowired MockMvc mvc;
    @Autowired InteriorDesignerRepository designerRepository;
    @Autowired DesignerService designerService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void lookupIgnoresCaseAndExcludesTheDesignersOwnAccount() {
        String number = newLicenseNumber();
        InteriorDesigner first = newDesigner("First Studio", number);

        assertThat(designerService.licenseReuseWarning(
                first.getId(), "  " + number + "  ")).isNull();

        InteriorDesigner second = newDesigner(
                "Second Studio", number.toLowerCase(Locale.ROOT));

        assertThat(designerRepository
                .findByCompanyLicenseNumberIgnoreCaseAndIdNot(
                        number, first.getId()))
                .extracting(InteriorDesigner::getId)
                .containsExactly(second.getId());

        assertThat(designerService.licenseReuseWarning(
                first.getId(), number))
                .contains("Second Studio")
                .doesNotContain("First Studio");
    }

    @Test
    void blankLicenseNumbersDoNotProduceWarnings() {
        assertThat(designerService.licenseReuseWarning(1L, null)).isNull();
        assertThat(designerService.licenseReuseWarning(1L, "")).isNull();
        assertThat(designerService.licenseReuseWarning(1L, "   ")).isNull();
    }

    @Test
    void duplicateLicenseAllowsRegistrationAndDisplaysAWarning()
            throws Exception {
        String number = newLicenseNumber();
        InteriorDesigner existing =
                newDesigner("Existing Registration Studio", number);
        String email = "registration-" + UUID.randomUUID() + "@example.test";

        var result = mvc.perform(post("/designer/register")
                        .with(csrf())
                        .param("contactName", "New Designer")
                        .param("email", email)
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD)
                        .param("companyName", "New Registration Studio")
                        .param("companyDescription", "Test company.")
                        .param("companyPhone", "12345")
                        .param("licenseNumber",
                                number.toLowerCase(Locale.ROOT))
                        .param("serviceArea", "Test Area"))
                .andExpect(redirectedUrl("/designer/login"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute(
                        "licenseWarning",
                        containsString("Existing Registration Studio")))
                .andReturn();

        var matches = designerRepository
                .findByCompanyLicenseNumberIgnoreCaseAndIdNot(
                        number, existing.getId());

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getEmail()).isEqualTo(email);
        assertThat(matches.get(0).getStatus())
                .isEqualTo(AccountStatus.PENDING);
        assertThat(matches.get(0).isVerified()).isFalse();

        mvc.perform(get("/designer/login")
                        .flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Existing Registration Studio")));
    }

    @Test
    void savingDuplicateLicenseDisplaysWarningWithoutBlockingTheUpdate()
            throws Exception {
        String number = newLicenseNumber();
        newDesigner("Existing Profile Studio", number);
        InteriorDesigner designer =
                newDesigner("Editing Studio", null);
        MockHttpSession session = loginDesigner(designer);

        mvc.perform(post("/designer/profile")
                        .session(session)
                        .with(csrf())
                        .param("companyName", "Editing Studio")
                        .param("companyDescription", "Updated company.")
                        .param("companyPhone", "12345")
                        .param("serviceArea", "Test Area")
                        .param("licenseNumber", number))
                .andExpect(redirectedUrl("/designer/profile/edit"))
                .andExpect(flash().attributeExists("successMessage"));

        InteriorDesigner updated = designerRepository
                .findById(designer.getId()).orElseThrow();

        assertThat(updated.getCompany().getLicenseNumber())
                .isEqualTo(number);
        assertThat(updated.isVerified()).isFalse();

        mvc.perform(get("/designer/profile/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Existing Profile Studio")))
                .andExpect(content().string(
                        containsString("This license number is also used by")));
    }

    @Test
    void adminSeesDuplicateWarningButCanVerifyAfterCheckingOwnership()
            throws Exception {
        String number = newLicenseNumber();
        newDesigner("Other Registered Studio", number);
        InteriorDesigner designer =
                newDesigner("Reviewed Studio", number);
        MockHttpSession admin = loginAdmin();
        String url = "/admin/users/" + designer.getId() + "/license";

        mvc.perform(get(url).session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Other Registered Studio")))
                .andExpect(content().string(
                        containsString("This license number is also used by")));

        mvc.perform(post(url + "/verify")
                        .session(admin)
                        .with(csrf())
                        .param("licenseNumber", number)
                        .param("companyName", "Reviewed Studio")
                        .param("note",
                                "Checked official records and shared company ownership."))
                .andExpect(redirectedUrl(url))
                .andExpect(flash().attributeExists("successMessage"));

        assertThat(designerRepository.findById(designer.getId())
                .orElseThrow().isVerified()).isTrue();
    }

    private String newLicenseNumber() {
        return "LIC-" + UUID.randomUUID()
                .toString().toUpperCase(Locale.ROOT);
    }

    private InteriorDesigner newDesigner(
            String companyName, String licenseNumber) {
        Company company = new Company(
                companyName, "Test company.", "12345");
        company.setLicenseNumber(licenseNumber);
        company.setServiceArea("Test Area");

        InteriorDesigner designer = new InteriorDesigner(
                "Test Designer",
                "designer-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode(PASSWORD),
                company);

        designer.approve();
        return designerRepository.save(designer);
    }

    private MockHttpSession loginDesigner(
            InteriorDesigner designer) throws Exception {
        return (MockHttpSession) mvc.perform(post("/designer/login")
                        .with(csrf())
                        .param("email", designer.getEmail())
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/designer/dashboard"))
                .andReturn().getRequest().getSession(false);
    }

    private MockHttpSession loginAdmin() throws Exception {
        return (MockHttpSession) mvc.perform(post("/admin/login")
                        .with(csrf())
                        .param("email", "admin@test.local")
                        .param("password", "Admin@12345"))
                .andExpect(redirectedUrl("/admin/users"))
                .andReturn().getRequest().getSession(false);
    }
}
