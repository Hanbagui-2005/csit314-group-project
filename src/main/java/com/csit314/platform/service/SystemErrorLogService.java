package com.csit314.platform.service;

import com.csit314.platform.model.SystemErrorLog;
import com.csit314.platform.repository.SystemErrorLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// PM-15
@Service
public class SystemErrorLogService {

    private final SystemErrorLogRepository systemErrorLogRepository;

    public SystemErrorLogService(SystemErrorLogRepository systemErrorLogRepository) {
        this.systemErrorLogRepository = systemErrorLogRepository;
    }

    @Transactional(readOnly = true)
    public List<SystemErrorLog> recent() {
        return systemErrorLogRepository.findTop50ByOrderByOccurredAtDesc();
    }
}
