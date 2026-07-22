package com.iuims.registrar.service.support;

import org.springframework.stereotype.Service;

@Service
public class GlobalTermService {

    private final RegistrarTermService registrarTermService;

    public GlobalTermService(RegistrarTermService registrarTermService) {
        this.registrarTermService = registrarTermService;
    }

    public String getCurrentGlobalTermCode() {
        return registrarTermService.getCurrentDbTermCode().orElse(null);
    }

    public String getCurrentStudentTermYear(int yearLevel) {
        return registrarTermService.getCurrentStudentTermYear(yearLevel).orElse(null);
    }

    public Integer getCurrentTermId() {
        return registrarTermService.getCurrentTermId().orElse(null);
    }

    public Integer getCurrentSemesterNumber() {
        return registrarTermService.getCurrentSemesterNumber().orElse(null);
    }
}
