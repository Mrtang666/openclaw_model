package com.example.spring.wechat.care.web;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.service.CareSessionService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class CareApiSupport {

    private final CareSessionService sessionService;

    public CareApiSupport(CareSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public CareActor patient(String authorization) {
        return requireRole(authorization, Set.of(MedicalRole.PATIENT));
    }

    public CareActor family(String authorization) {
        return requireRole(authorization, Set.of(MedicalRole.CAREGIVER, MedicalRole.FAMILY));
    }

    public CareActor clinical(String authorization) {
        CareActor actor = sessionService.authenticate(authorization);
        if (!actor.role().isClinical()) {
            throw new CareException(CareErrorCode.FORBIDDEN, "当前账号不是有效的医护角色");
        }
        return actor;
    }

    public CareActor authenticated(String authorization) {
        return sessionService.authenticate(authorization);
    }

    public String traceId(String supplied) {
        String value = supplied == null ? "" : supplied.strip();
        return value.isBlank() ? UUID.randomUUID().toString() : limit(value, 64);
    }

    private CareActor requireRole(String authorization, Set<MedicalRole> roles) {
        CareActor actor = sessionService.authenticate(authorization);
        if (!roles.contains(actor.role())) {
            throw new CareException(CareErrorCode.FORBIDDEN, "当前账号角色不能访问该端口");
        }
        return actor;
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
