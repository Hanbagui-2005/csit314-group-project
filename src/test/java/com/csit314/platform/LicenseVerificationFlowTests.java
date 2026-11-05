package com.csit314.platform;

import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.LicenseVerification;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.LicenseVerificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LicenseVerificationFlowTests {

    private static final String DESIGNER_PASSWORD = "Designer!2026";

    @Autowired MockMvc mvc;
    @Autowired InteriorDesignerRepository designerRepository;
    @Autowired LicenseVerificationRepository verificationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void verificationAndRevocationPersistHistoryAndRenderThePage()
            throws Exception {
        InteriorDesigner designer = newDesigner("LIC-12345");
        MockHttpSession admin = loginAdmin();
        String url = licenseUrl(designer);

        mvc.perform(get(url).session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("LIC-12345")));

        verifyLicense(designer, admin);

        assertThat(reload(designer).isVerified()).isTrue();

        List<LicenseVerification> history = history(designer);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getAction())
                .isEqualTo(LicenseVerification.Action.VERIFIED);
        assertThat(history.get(0).getLicenseNumber()).isEqualTo("LIC-12345");
        assertThat(history.get(0).getCompanyName())
                .isEqualTo("Verification Test Studio");
        assertThat(history.get(0).getActorEmail())
                .isEqualTo("admin@test.local");
        assertThat(history.get(0).getRecordedAt()).isNotNull();

        mvc.perform(get(url).session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Checked official registration.")));

        mvc.perform(post(url + "/revoke")
                        .session(admin)
                        .with(csrf())
                        .param("licenseNumber", "LIC-12345")
                        .param("companyName", "Verification Test Studio")
                        .param("note", "Company details no longer match."))
                .andExpect(redirectedUrl(url))
                .andExpect(flash().attributeExists("successMessage"));

        assertThat(reload(designer).isVerified()).isFalse();

        history = history(designer);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getAction())
                .isEqualTo(LicenseVerification.Action.REVOKED);
        assertThat(history.get(0).getNote())
                .isEqualTo("Company details no longer match.");

        mvc.perform(get(url).session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Company details no longer match.")));
    }

    @Test
    void designerLicenseChangeClearsVerificationAndPreservesHistory()
            throws Exception {
        InteriorDesigner designer = newDesigner("LIC-12345");
        MockHttpSession admin = loginAdmin();
        verifyLicense(designer, admin);

        MockHttpSession designerSession =
                (MockHttpSession) mvc.perform(post("/designer/login")
                                .with(csrf())
                                .param("email", designer.getEmail())
                                .param("password", DESIGNER_PASSWORD))
                        .andExpect(redirectedUrl("/designer/dashboard"))
                        .andReturn().getRequest().getSession(false);

        mvc.perform(post("/designer/profile")
                        .session(designerSession)
                        .with(csrf())
                        .param("companyName", "Verification Test Studio")
                        .param("companyDescription", "Testing company profile.")
                        .param("companyPhone", "12345")
                        .param("serviceArea", "Test Area")
                        .param("licenseNumber", "LIC-67890"))
                .andExpect(redirectedUrl("/designer/profile/edit"))
                .andExpect(flash().attributeExists("successMessage"));

        InteriorDesigner updated = reload(designer);
        assertThat(updated.isVerified()).isFalse();
        assertThat(updated.getCompany().getLicenseNumber())
                .isEqualTo("LIC-67890");

        List<LicenseVerification> history = history(designer);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getAction())
                .isEqualTo(LicenseVerification.Action.LICENSE_CHANGED);
        assertThat(history.get(0).getLicenseNumber()).isEqualTo("LIC-67890");
        assertThat(history.get(0).getActorEmail())
                .isEqualTo(designer.getEmail());
        assertThat(history.get(0).getNote())
                .contains("LIC-12345", "LIC-67890");
        assertThat(history.get(1).getLicenseNumber()).isEqualTo("LIC-12345");

        mvc.perform(get("/designer/profile/edit").session(designerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("LIC-67890")));

        mvc.perform(post(licenseUrl(designer) + "/verify")
                        .session(admin)
                        .with(csrf())
                        .param("licenseNumber", "LIC-12345")
                        .param("companyName", "Verification Test Studio")
                        .param("note", "Using an outdated page."))
                .andExpect(flash().attribute(
                        "errorMessage", containsString("changed")));

        assertThat(reload(designer).isVerified()).isFalse();
        assertThat(history(designer)).hasSize(2);
    }

    // ADM-05 / code review doc - an unverified designer renaming their company (same
    // license number) between the admin opening the page and submitting it must not let
    // the admin verify a company name they never saw
    @Test
    void adminCannotVerifyACompanyRenamedSinceThePageWasLoaded()
            throws Exception {
        InteriorDesigner designer = newDesigner("LIC-12345");
        MockHttpSession admin = loginAdmin();

        mvc.perform(get(licenseUrl(designer)).session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("name=\"companyName\"")));

        MockHttpSession designerSession = loginDesigner(designer);
        mvc.perform(post("/designer/profile")
                        .session(designerSession)
                        .with(csrf())
                        .param("companyName", "Different Legal Entity")
                        .param("companyDescription", "Testing company profile.")
                        .param("companyPhone", "12345")
                        .param("serviceArea", "Test Area")
                        .param("licenseNumber", "LIC-12345"))
                .andExpect(redirectedUrl("/designer/profile/edit"));

        var rejected = mvc.perform(post(licenseUrl(designer) + "/verify")
                        .session(admin)
                        .with(csrf())
                        .param("licenseNumber", "LIC-12345")
                        .param("companyName", "Verification Test Studio")
                        .param("note", "Checked the old company's registration."))
                .andExpect(redirectedUrl(licenseUrl(designer)))
                .andExpect(flash().attribute(
                        "errorMessage", containsString("company name has changed")))
                .andReturn();

        // the admin actually sees the reason on the page they land back on
        mvc.perform(get(licenseUrl(designer))
                        .session(admin)
                        .flashAttrs(rejected.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("company name has changed")));

        assertThat(reload(designer).isVerified()).isFalse();
        assertThat(history(designer)).isEmpty();
    }

    // a verified company renamed with the same license number loses the badge, and the
    // history entry records the new name that now needs checking
    @Test
    void renamingAVerifiedCompanyClearsVerificationAndRecordsTheNewName()
            throws Exception {
        InteriorDesigner designer = newDesigner("LIC-12345");
        verifyLicense(designer, loginAdmin());

        mvc.perform(post("/designer/profile")
                        .session(loginDesigner(designer))
                        .with(csrf())
                        .param("companyName", "Different Legal Entity")
                        .param("companyDescription", "Testing company profile.")
                        .param("companyPhone", "12345")
                        .param("serviceArea", "Test Area")
                        .param("licenseNumber", "LIC-12345"))
                .andExpect(redirectedUrl("/designer/profile/edit"));

        assertThat(reload(designer).isVerified()).isFalse();
        List<LicenseVerification> history = history(designer);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getAction())
                .isEqualTo(LicenseVerification.Action.LICENSE_CHANGED);
        assertThat(history.get(0).getCompanyName())
                .isEqualTo("Different Legal Entity");
        assertThat(history.get(1).getCompanyName())
                .isEqualTo("Verification Test Studio");
    }

    // code review doc - login used to save() the whole account it loaded, which could
    // write a stale verified flag/company back over an admin's change. A designer whose
    // verification was revoked must stay revoked after logging in again
    @Test
    void designerLoginDoesNotRestoreARevokedVerification() throws Exception {
        InteriorDesigner designer = newDesigner("LIC-12345");
        MockHttpSession admin = loginAdmin();
        verifyLicense(designer, admin);

        mvc.perform(post(licenseUrl(designer) + "/revoke")
                        .session(admin)
                        .with(csrf())
                        .param("licenseNumber", "LIC-12345")
                        .param("companyName", "Verification Test Studio")
                        .param("note", "Registration lapsed."))
                .andExpect(flash().attributeExists("successMessage"));

        loginDesigner(designer);

        InteriorDesigner after = reload(designer);
        assertThat(after.isVerified()).isFalse();
        assertThat(after.getLastLoginAt()).isNotNull();
        assertThat(after.getCompany().getName()).isEqualTo("Verification Test Studio");
    }

    private MockHttpSession loginDesigner(InteriorDesigner designer) throws Exception {
        return (MockHttpSession) mvc.perform(post("/designer/login")
                        .with(csrf())
                        .param("email", designer.getEmail())
                        .param("password", DESIGNER_PASSWORD))
                .andExpect(redirectedUrl("/designer/dashboard"))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void blankEvidenceDoesNotVerifyOrCreateHistory() throws Exception {
        InteriorDesigner designer = newDesigner("LIC-12345");

        mvc.perform(post(licenseUrl(designer) + "/verify")
                        .session(loginAdmin())
                        .with(csrf())
                        .param("licenseNumber", "LIC-12345")
                        .param("companyName", "Verification Test Studio")
                        .param("note", "   "))
                .andExpect(flash().attributeExists("errorMessage"));

        assertThat(reload(designer).isVerified()).isFalse();
        assertThat(history(designer)).isEmpty();
    }

    @Test
    void missingLicensePageRendersAndRejectsVerification() throws Exception {
        InteriorDesigner designer = newDesigner(null);
        MockHttpSession admin = loginAdmin();

        mvc.perform(get(licenseUrl(designer)).session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("No license number has been provided")));

        mvc.perform(post(licenseUrl(designer) + "/verify")
                        .session(admin)
                        .with(csrf())
                        .param("companyName", "Verification Test Studio")
                        .param("note", "Checked registration."))
                .andExpect(flash().attribute(
                        "errorMessage", containsString("license number")));

        assertThat(reload(designer).isVerified()).isFalse();
        assertThat(history(designer)).isEmpty();
    }

    @Test
    void licensePageRequiresAdminLogin() throws Exception {
        InteriorDesigner designer = newDesigner("LIC-12345");

        mvc.perform(get(licenseUrl(designer)))
                .andExpect(redirectedUrl("/admin/login"));
    }

    private InteriorDesigner newDesigner(String licenseNumber) {
        Company company = new Company(
                "Verification Test Studio", "Testing company profile.", "12345");
        company.setLicenseNumber(licenseNumber);
        company.setServiceArea("Test Area");

        InteriorDesigner designer = new InteriorDesigner(
                "Test Designer",
                "license-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode(DESIGNER_PASSWORD),
                company);

        designer.approve();
        return designerRepository.save(designer);
    }

    private MockHttpSession loginAdmin() throws Exception {
        return (MockHttpSession) mvc.perform(post("/admin/login")
                        .with(csrf())
                        .param("email", "admin@test.local")
                        .param("password", "Admin@12345"))
                .andExpect(redirectedUrl("/admin/users"))
                .andReturn().getRequest().getSession(false);
    }

    private void verifyLicense(
            InteriorDesigner designer, MockHttpSession admin) throws Exception {
        mvc.perform(post(licenseUrl(designer) + "/verify")
                        .session(admin)
                        .with(csrf())
                        .param("licenseNumber", "LIC-12345")
                        .param("companyName", "Verification Test Studio")
                        .param("note", "Checked official registration."))
                .andExpect(redirectedUrl(licenseUrl(designer)))
                .andExpect(flash().attributeExists("successMessage"));
    }

    private String licenseUrl(InteriorDesigner designer) {
        return "/admin/users/" + designer.getId() + "/license";
    }

    private InteriorDesigner reload(InteriorDesigner designer) {
        return designerRepository.findById(designer.getId()).orElseThrow();
    }

    private List<LicenseVerification> history(InteriorDesigner designer) {
        return verificationRepository
                .findByDesignerIdOrderByRecordedAtDescIdDesc(designer.getId());
    }
}
