package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CareMemoryEvent;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.model.MemoryEventStatus;
import com.example.spring.wechat.care.repository.CareRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CareMemoryServiceTests {

    private final CareRecordRepository repository = mock(CareRecordRepository.class);
    private final CareAuthorizationService authorizationService = mock(CareAuthorizationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);
    private final CareMemoryService service = new CareMemoryService(repository, authorizationService, clock);

    @Test
    void patientRecordsMemoryAsWaitingForConfirmation() {
        when(repository.findMemoryByIdempotencyKey("memory:1:memory-1")).thenReturn(Optional.empty());
        when(repository.saveMemory(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CareMemoryEvent result = service.record(
                new CareActor(1L, "PAT-1", "患者", MedicalRole.PATIENT),
                new CareMemoryService.RecordCommand(
                        "下周二复查", "下周二复查", null, "[]", "医院", "CARE_TEAM", "msg-1", "memory-1"));

        assertThat(result.patientUserId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(MemoryEventStatus.WAITING_CONFIRMATION);
        assertThat(result.originalText()).isEqualTo("下周二复查");
    }

    @Test
    void caregiverCannotCreatePatientMemoryInFirstPhase() {
        assertThatThrownBy(() -> service.record(
                new CareActor(2L, "CAR-1", "家属", MedicalRole.CAREGIVER),
                new CareMemoryService.RecordCommand("内容", "", null, "", "", "CARE_TEAM", "", "")))
                .isInstanceOf(CareException.class)
                .hasMessageContaining("患者本人");
    }
}
