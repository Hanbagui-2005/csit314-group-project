package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Message;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.MessageRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.web.dto.ContactDesignerForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Taiga #25 [IDR-13] - a customer messaging a designer about their project. */
@ExtendWith(MockitoExtension.class)
class MessageServiceUnitTests {

    @Mock MessageRepository messageRepository;
    @Mock InteriorDesignerRepository designerRepository;
    @Mock ProjectRepository projectRepository;
    MessageService service;

    Customer customer;
    InteriorDesigner designer;
    InteriorDesigner otherDesigner;
    Category category;
    Project project;

    @BeforeEach
    void setUp() {
        service = new MessageService(messageRepository, designerRepository, projectRepository);

        customer = new Customer("Jordan Lee", "jordan@example.test", "encoded");
        ReflectionTestUtils.setField(customer, "id", 1L);

        designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        ReflectionTestUtils.setField(designer, "id", 2L);
        designer.setStatus(AccountStatus.ACTIVE);

        otherDesigner = new InteriorDesigner("Other", "other@example.test", "encoded",
                new Company("Other Studio", "Description", "67890"));
        ReflectionTestUtils.setField(otherDesigner, "id", 3L);
        otherDesigner.setStatus(AccountStatus.ACTIVE);

        category = new Category("Kitchen");
        ReflectionTestUtils.setField(category, "id", 10L);

        project = new Project("Kitchen remodel", "Description", category, designer);
        ReflectionTestUtils.setField(project, "id", 100L);
    }

    @Test
    void sendsAGeneralMessageWithNoProject() {
        when(designerRepository.findById(2L)).thenReturn(Optional.of(designer));
        when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Message result = service.sendMessage(customer, 2L, new ContactDesignerForm("Hi, interested in your work", null));

        assertThat(result.getSender()).isEqualTo(customer);
        assertThat(result.getRecipient()).isEqualTo(designer);
        assertThat(result.getProject()).isNull();
        assertThat(result.getBody()).isEqualTo("Hi, interested in your work");
    }

    @Test
    void sendsAMessageAboutASpecificProject() {
        when(designerRepository.findById(2L)).thenReturn(Optional.of(designer));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        when(messageRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.sendMessage(customer, 2L, new ContactDesignerForm("Love this kitchen, can we talk?", 100L));

        assertThat(captor.getValue().getProject()).isEqualTo(project);
    }

    @Test
    void rejectsAProjectThatBelongsToADifferentDesigner() {
        when(designerRepository.findById(3L)).thenReturn(Optional.of(otherDesigner));
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.sendMessage(customer, 3L, new ContactDesignerForm("Hi", 100L)))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsAnEmptyMessage() {
        assertThatThrownBy(() -> service.sendMessage(customer, 2L, new ContactDesignerForm("  ", null)))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(designerRepository);
    }

    @Test
    void rejectsMessagingADesignerWhoIsNotActive() {
        designer.setStatus(AccountStatus.SUSPENDED);
        when(designerRepository.findById(2L)).thenReturn(Optional.of(designer));

        assertThatThrownBy(() -> service.sendMessage(customer, 2L, new ContactDesignerForm("Hi", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("accepting messages");
    }

    @Test
    void designerCanMarkTheirOwnMessageRead() {
        Message message = new Message(customer, designer, null, "Hi");
        ReflectionTestUtils.setField(message, "id", 500L);
        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));

        service.markRead(designer, 500L);

        assertThat(message.isRead()).isTrue();
    }

    @Test
    void anotherDesignerCannotMarkSomeoneElsesMessageRead() {
        Message message = new Message(customer, designer, null, "Hi");
        ReflectionTestUtils.setField(message, "id", 500L);
        when(messageRepository.findById(500L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.markRead(otherDesigner, 500L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("your own messages");
        assertThat(message.isRead()).isFalse();
    }
}
