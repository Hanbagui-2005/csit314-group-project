package com.csit314.platform.service;

import com.csit314.platform.model.SystemErrorLog;
import com.csit314.platform.repository.SystemErrorLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// PM-15
@ExtendWith(MockitoExtension.class)
class SystemErrorLogServiceUnitTests {

    @Mock SystemErrorLogRepository systemErrorLogRepository;
    SystemErrorLogService service;

    @BeforeEach
    void setUp() {
        service = new SystemErrorLogService(systemErrorLogRepository);
    }

    @Test
    void recentDelegatesToTheRepositorysMostRecentFirstQuery() {
        SystemErrorLog error = new SystemErrorLog("/search", "NullPointerException: boom");
        when(systemErrorLogRepository.findTop50ByOrderByOccurredAtDesc()).thenReturn(List.of(error));

        assertThat(service.recent()).containsExactly(error);
    }
}
