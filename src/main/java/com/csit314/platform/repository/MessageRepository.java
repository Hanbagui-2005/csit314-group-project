package com.csit314.platform.repository;

import com.csit314.platform.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByRecipientIdOrderBySentAtDesc(Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    // IDR-08
    long countByRecipientIdAndSentAtAfter(Long recipientId, LocalDateTime since);
}
