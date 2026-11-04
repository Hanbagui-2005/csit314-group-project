package com.csit314.platform.service;

import com.csit314.platform.dto.CompanyProfileForm;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        service = new DesignerService(designerRepository, userAccountRepository, passwordEncoder);
        Company company = new Company("Studio Lume", "Original description", "+61 400 000 000");
        company.setServiceArea("Sydney Metro, NSW");
        designer = new InteriorDesigner("Jordan Lee", "jordan@example.test", "encoded", company);
        ReflectionTestUtils.setField(designer, "id", 7L);
        designer.setServiceAreaVerified(true);
        when(designerRepository.findById(7L)).thenReturn(Optional.of(designer));
    }

    @Test
    void updatesTheCompanysNameDescriptionAndPhone() {
        CompanyProfileForm form = new CompanyProfileForm("New Studio Name", "New description",
                "+61 400 111 222", "Sydney Metro, NSW");

        service.updateCompanyProfile(7L, form);

        assertThat(designer.getCompany().getName()).isEqualTo("New Studio Name");
        assertThat(designer.getCompany().getDescription()).isEqualTo("New description");
        assertThat(designer.getCompany().getPhone()).isEqualTo("+61 400 111 222");
    }

    // Anthony's review - with open-in-view off, a designer the caller already loaded
    // (in its own now-closed transaction) is detached and mutating it never gets
    // flushed. This is why the method takes an id and loads its own entity instead -
    // this test is the proof: it never hands the service a designer object at all
    @Test
    void loadsTheDesignerItselfInsteadOfRelyingOnAnAlreadyLoadedOne() {
        CompanyProfileForm form = new CompanyProfileForm("New Studio Name", "New description",
                "+61 400 111 222", "Sydney Metro, NSW");

        service.updateCompanyProfile(7L, form);

        verify(designerRepository).findById(7L);
    }

    @Test
    void rejectsABlankCompanyName() {
        CompanyProfileForm form = new CompanyProfileForm("   ", "Description", "phone", "area");

        assertThatThrownBy(() -> service.updateCompanyProfile(7L, form))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Company name");
    }

    // PM-05 - an unchanged service area keeps whatever verification state it already had
    @Test
    void leavesServiceAreaVerificationAloneWhenTheAreaDoesNotChange() {
        CompanyProfileForm form = new CompanyProfileForm("Studio Lume", "Original description",
                "+61 400 000 000", "Sydney Metro, NSW");

        service.updateCompanyProfile(7L, form);

        assertThat(designer.isServiceAreaVerified()).isTrue();
    }

    @Test
    void clearsServiceAreaVerificationWhenTheAreaChanges() {
        CompanyProfileForm form = new CompanyProfileForm("Studio Lume", "Original description",
                "+61 400 000 000", "Melbourne Metro, VIC");

        service.updateCompanyProfile(7L, form);

        assertThat(designer.getCompany().getServiceArea()).isEqualTo("Melbourne Metro, VIC");
        assertThat(designer.isServiceAreaVerified()).isFalse();
    }
}
