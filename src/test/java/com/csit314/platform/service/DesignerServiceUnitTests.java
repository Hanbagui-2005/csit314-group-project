package com.csit314.platform.service;

import com.csit314.platform.dto.CompanyProfileForm;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesignerServiceUnitTests {

    @Mock InteriorDesignerRepository designerRepository;
    @Mock UserAccountRepository userAccountRepository;
    @Mock PasswordEncoder passwordEncoder;

    DesignerService service;
    InteriorDesigner designer;

    @BeforeEach
    void setUp() {
        service = new DesignerService(
                designerRepository, userAccountRepository, passwordEncoder);

        Company company = new Company(
                "Studio Lume", "Original description", "+61 400 000 000");
        company.setServiceArea("Sydney Metro, NSW");

        designer = new InteriorDesigner(
                "Jordan Lee", "jordan@example.test", "encoded", company);

        ReflectionTestUtils.setField(designer, "id", 7L);
        designer.setServiceAreaVerified(true);

        when(designerRepository.findById(7L))
                .thenReturn(Optional.of(designer));
    }

    private CompanyProfileForm profileWithLicense(String licenseNumber) {
        return new CompanyProfileForm(
                "Studio Lume",
                "Original description",
                "+61 400 000 000",
                "Sydney Metro, NSW",
                licenseNumber);
    }

    @Test
    void updatesTheCompanysNameDescriptionAndPhone() {
        CompanyProfileForm form = new CompanyProfileForm(
                "New Studio Name", "New description",
                "+61 400 111 222", "Sydney Metro, NSW", null);

        service.updateCompanyProfile(7L, form);

        assertThat(designer.getCompany().getName())
                .isEqualTo("New Studio Name");
        assertThat(designer.getCompany().getDescription())
                .isEqualTo("New description");
        assertThat(designer.getCompany().getPhone())
                .isEqualTo("+61 400 111 222");
    }

    @Test
    void loadsTheDesignerItselfInsteadOfRelyingOnAnAlreadyLoadedOne() {
        service.updateCompanyProfile(7L, profileWithLicense(null));

        verify(designerRepository).findById(7L);
    }

    @Test
    void rejectsABlankCompanyName() {
        CompanyProfileForm form = new CompanyProfileForm(
                "   ", "Description", "phone", "area", null);

        assertThatThrownBy(() -> service.updateCompanyProfile(7L, form))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Company name");
    }

    @Test
    void leavesServiceAreaVerificationAloneWhenTheAreaDoesNotChange() {
        service.updateCompanyProfile(7L, profileWithLicense(null));

        assertThat(designer.isServiceAreaVerified()).isTrue();
    }

    @Test
    void clearsServiceAreaVerificationWhenTheAreaChanges() {
        CompanyProfileForm form = new CompanyProfileForm(
                "Studio Lume", "Original description",
                "+61 400 000 000", "Melbourne Metro, VIC", null);

        service.updateCompanyProfile(7L, form);

        assertThat(designer.getCompany().getServiceArea())
                .isEqualTo("Melbourne Metro, VIC");
        assertThat(designer.isServiceAreaVerified()).isFalse();
    }

    @Test
    void addsALicenseNumberWithoutAutomaticallyVerifying() {
        service.updateCompanyProfile(
                7L, profileWithLicense("LIC-12345"));

        assertThat(designer.getCompany().getLicenseNumber())
                .isEqualTo("LIC-12345");
        assertThat(designer.isVerified()).isFalse();
    }

    @Test
    void changingLicenseNumberClearsVerification() {
        designer.getCompany().setLicenseNumber("LIC-12345");
        designer.setVerified(true);

        service.updateCompanyProfile(
                7L, profileWithLicense("LIC-67890"));

        assertThat(designer.getCompany().getLicenseNumber())
                .isEqualTo("LIC-67890");
        assertThat(designer.isVerified()).isFalse();
        assertThat(designer.isServiceAreaVerified()).isTrue();
    }

    @Test
    void savingTheSameLicenseNumberKeepsVerification() {
        designer.getCompany().setLicenseNumber("LIC-12345");
        designer.setVerified(true);

        service.updateCompanyProfile(
                7L, profileWithLicense("LIC-12345"));

        assertThat(designer.getCompany().getLicenseNumber())
                .isEqualTo("LIC-12345");
        assertThat(designer.isVerified()).isTrue();
    }

    @Test
    void surroundingWhitespaceDoesNotClearVerification() {
        designer.getCompany().setLicenseNumber("LIC-12345");
        designer.setVerified(true);

        service.updateCompanyProfile(
                7L, profileWithLicense("  LIC-12345  "));

        assertThat(designer.getCompany().getLicenseNumber())
                .isEqualTo("LIC-12345");
        assertThat(designer.isVerified()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void removingLicenseNumberClearsVerification(String licenseNumber) {
        designer.getCompany().setLicenseNumber("LIC-12345");
        designer.setVerified(true);

        service.updateCompanyProfile(
                7L, profileWithLicense(licenseNumber));

        assertThat(designer.getCompany().getLicenseNumber()).isNull();
        assertThat(designer.isVerified()).isFalse();
    }

    @Test
    void rejectsAnOverlongLicenseWithoutChangingExistingVerification() {
        designer.getCompany().setLicenseNumber("LIC-12345");
        designer.setVerified(true);

        assertThatThrownBy(() -> service.updateCompanyProfile(
                7L, profileWithLicense("X".repeat(61))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("License number");

        assertThat(designer.getCompany().getLicenseNumber())
                .isEqualTo("LIC-12345");
        assertThat(designer.isVerified()).isTrue();
    }
}
