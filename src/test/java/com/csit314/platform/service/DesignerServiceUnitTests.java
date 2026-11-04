package com.csit314.platform.service;

import com.csit314.platform.dto.CompanyProfileForm;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.LicenseVerification;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.LicenseVerificationRepository;
import com.csit314.platform.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesignerServiceUnitTests {

    @Mock InteriorDesignerRepository designerRepository;
    @Mock UserAccountRepository userAccountRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock LicenseVerificationRepository verificationRepository;
    @Mock EntityManager entityManager;

    DesignerService service;
    InteriorDesigner designer;

    @BeforeEach
    void setUp() {
        service = new DesignerService(
                designerRepository,
                userAccountRepository,
                passwordEncoder,
                verificationRepository,
                entityManager);

        Company company = new Company(
                "Studio Lume", "Original description", "+61 400 000 000");
        company.setServiceArea("Sydney Metro, NSW");

        designer = new InteriorDesigner(
                "Jordan Lee", "jordan@example.test", "encoded", company);

        ReflectionTestUtils.setField(designer, "id", 7L);
        designer.setServiceAreaVerified(true);
    }

    private void stubDesigner() {
        when(entityManager.find(
                InteriorDesigner.class,
                7L,
                LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(designer);
    }

    private CompanyProfileForm profileWithLicense(String licenseNumber) {
        return new CompanyProfileForm(
                "Studio Lume",
                "Original description",
                "+61 400 000 000",
                "Sydney Metro, NSW",
                licenseNumber);
    }

    private LicenseVerification savedRecord() {
        ArgumentCaptor<LicenseVerification> captor =
                ArgumentCaptor.forClass(LicenseVerification.class);
        verify(verificationRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void updatesTheCompanysNameDescriptionAndPhone() {
        stubDesigner();
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
        verifyNoInteractions(verificationRepository);
    }

    @Test
    void loadsAndRefreshesTheDesignerUnderALock() {
        stubDesigner();

        service.updateCompanyProfile(7L, profileWithLicense(null));

        verify(entityManager).find(
                InteriorDesigner.class,
                7L,
                LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(designer);
    }

    @Test
    void rejectsABlankCompanyName() {
        CompanyProfileForm form = new CompanyProfileForm(
                "   ", "Description", "phone", "area", null);

        assertThatThrownBy(() -> service.updateCompanyProfile(7L, form))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Company name");

        verifyNoInteractions(entityManager, verificationRepository);
    }

    @Test
    void leavesServiceAreaVerificationAloneWhenTheAreaDoesNotChange() {
        stubDesigner();

        service.updateCompanyProfile(7L, profileWithLicense(null));

        assertThat(designer.isServiceAreaVerified()).isTrue();
    }

    @Test
    void clearsServiceAreaVerificationWhenTheAreaChanges() {
        stubDesigner();
        CompanyProfileForm form = new CompanyProfileForm(
                "Studio Lume", "Original description",
                "+61 400 000 000", "Melbourne Metro, VIC", null);

        service.updateCompanyProfile(7L, form);

        assertThat(designer.getCompany().getServiceArea())
                .isEqualTo("Melbourne Metro, VIC");
        assertThat(designer.isServiceAreaVerified()).isFalse();
    }

    @Test
    void addingALicenseRecordsTheChangeWithoutVerifying() {
        stubDesigner();

        service.updateCompanyProfile(
                7L, profileWithLicense("LIC-12345"));

        assertThat(designer.getCompany().getLicenseNumber())
                .isEqualTo("LIC-12345");
        assertThat(designer.isVerified()).isFalse();

        LicenseVerification record = savedRecord();
        assertThat(record.getDesigner()).isSameAs(designer);
        assertThat(record.getAction())
                .isEqualTo(LicenseVerification.Action.LICENSE_CHANGED);
        assertThat(record.getLicenseNumber()).isEqualTo("LIC-12345");
        assertThat(record.getActorEmail()).isEqualTo("jordan@example.test");
        assertThat(record.getNote()).contains("not provided", "LIC-12345");
    }

    @Test
    void changingLicenseClearsVerificationAndRecordsBothNumbers() {
        stubDesigner();
        designer.getCompany().setLicenseNumber("LIC-12345");
        designer.setVerified(true);

        service.updateCompanyProfile(
                7L, profileWithLicense("LIC-67890"));

        assertThat(designer.getCompany().getLicenseNumber())
                .isEqualTo("LIC-67890");
        assertThat(designer.isVerified()).isFalse();
        assertThat(designer.isServiceAreaVerified()).isTrue();

        LicenseVerification record = savedRecord();
        assertThat(record.getAction())
                .isEqualTo(LicenseVerification.Action.LICENSE_CHANGED);
        assertThat(record.getLicenseNumber()).isEqualTo("LIC-67890");
        assertThat(record.getActorEmail()).isEqualTo("jordan@example.test");
        assertThat(record.getNote()).contains("LIC-12345", "LIC-67890");
    }

    // Anthony's review on PR #27 - renaming a verified company used to keep the verified
    // badge even though the name an admin actually checked no longer matches
    @Test
    void renamingAVerifiedCompanyClearsVerificationAndRecordsTheChange() {
        stubDesigner();
        designer.getCompany().setLicenseNumber("LIC-12345");
        designer.setVerified(true);
        CompanyProfileForm form = new CompanyProfileForm(
                "New Studio Name", "Original description",
                "+61 400 000 000", "Sydney Metro, NSW", "LIC-12345");

        service.updateCompanyProfile(7L, form);

        assertThat(designer.getCompany().getName()).isEqualTo("New Studio Name");
        assertThat(designer.isVerified()).isFalse();

        LicenseVerification record = savedRecord();
        assertThat(record.getAction()).isEqualTo(LicenseVerification.Action.LICENSE_CHANGED);
        assertThat(record.getNote()).contains("Studio Lume", "New Studio Name");
    }

    // renaming a company that was never verified has nothing to invalidate yet
    @Test
    void renamingAnUnverifiedCompanyDoesNotAddHistory() {
        stubDesigner();
        CompanyProfileForm form = new CompanyProfileForm(
                "New Studio Name", "New description",
                "+61 400 111 222", "Sydney Metro, NSW", null);

        service.updateCompanyProfile(7L, form);

        assertThat(designer.getCompany().getName()).isEqualTo("New Studio Name");
        assertThat(designer.isVerified()).isFalse();
        verifyNoInteractions(verificationRepository);
    }

    @Test
    void savingTheSameLicenseKeepsVerificationWithoutAddingHistory() {
        stubDesigner();
        designer.getCompany().setLicenseNumber("LIC-12345");
        designer.setVerified(true);

        service.updateCompanyProfile(
                7L, profileWithLicense("LIC-12345"));

        assertThat(designer.isVerified()).isTrue();
        verifyNoInteractions(verificationRepository);
    }

    @Test
    void surroundingWhitespaceDoesNotClearVerificationOrAddHistory() {
        stubDesigner();
        designer.getCompany().setLicenseNumber("LIC-12345");
        designer.setVerified(true);

        service.updateCompanyProfile(
                7L, profileWithLicense("  LIC-12345  "));

        assertThat(designer.getCompany().getLicenseNumber())
                .isEqualTo("LIC-12345");
        assertThat(designer.isVerified()).isTrue();
        verifyNoInteractions(verificationRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void removingLicenseClearsVerificationAndRecordsTheChange(
            String licenseNumber) {
        stubDesigner();
        designer.getCompany().setLicenseNumber("LIC-12345");
        designer.setVerified(true);

        service.updateCompanyProfile(
                7L, profileWithLicense(licenseNumber));

        assertThat(designer.getCompany().getLicenseNumber()).isNull();
        assertThat(designer.isVerified()).isFalse();

        LicenseVerification record = savedRecord();
        assertThat(record.getAction())
                .isEqualTo(LicenseVerification.Action.LICENSE_CHANGED);
        assertThat(record.getLicenseNumber()).isNull();
        assertThat(record.getNote()).contains("LIC-12345", "not provided");
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
        verifyNoInteractions(entityManager, verificationRepository);
    }

    @Test
    void cannotUpdateAMissingDesigner() {
        assertThatThrownBy(() -> service.updateCompanyProfile(
                7L, profileWithLicense("LIC-12345")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("not found");

        verifyNoInteractions(verificationRepository);
    }
}
