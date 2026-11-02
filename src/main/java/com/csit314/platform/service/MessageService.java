package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Message;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.MessageRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.web.dto.ContactDesignerForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// IDR-13
@Service
public class MessageService {

    private static final int MAX_BODY_LENGTH = 2000;

    private final MessageRepository messageRepository;
    private final InteriorDesignerRepository designerRepository;
    private final ProjectRepository projectRepository;

    public MessageService(MessageRepository messageRepository,
                          InteriorDesignerRepository designerRepository,
                          ProjectRepository projectRepository) {
        this.messageRepository = messageRepository;
        this.designerRepository = designerRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Message sendMessage(Customer sender, Long designerId, ContactDesignerForm form) {
        String body = InputRules.requireText(form.body(), "Message", MAX_BODY_LENGTH);

        InteriorDesigner designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new ServiceException("That designer no longer exists."));
        if (designer.getStatus() != AccountStatus.ACTIVE) {
            throw new ServiceException("This designer isn't accepting messages right now.");
        }

        Project project = null;
        if (form.projectId() != null) {
            project = projectRepository.findById(form.projectId())
                    .orElseThrow(() -> new ServiceException("That project no longer exists."));
            if (!project.getDesigner().getId().equals(designerId)) {
                throw new ServiceException("That project doesn't belong to this designer.");
            }
        }

        return messageRepository.save(new Message(sender, designer, project, body));
    }

    public List<Message> inbox(InteriorDesigner designer) {
        return messageRepository.findByRecipientIdOrderBySentAtDesc(designer.getId());
    }

    public long unreadCount(InteriorDesigner designer) {
        return messageRepository.countByRecipientIdAndReadFalse(designer.getId());
    }

    @Transactional
    public void markRead(InteriorDesigner designer, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ServiceException("Message not found."));
        if (!message.getRecipient().getId().equals(designer.getId())) {
            throw new ServiceException("You can only manage your own messages.");
        }
        message.markRead();
    }
}
