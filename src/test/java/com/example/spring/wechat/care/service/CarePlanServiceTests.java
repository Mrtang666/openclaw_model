package com.example.spring.wechat.care.service;

import com.example.spring.wechat.care.exception.CareErrorCode;
import com.example.spring.wechat.care.exception.CareException;
import com.example.spring.wechat.care.model.CareActor;
import com.example.spring.wechat.care.model.CarePlan;
import com.example.spring.wechat.care.model.CarePlanDetails;
import com.example.spring.wechat.care.model.CarePlanStatus;
import com.example.spring.wechat.care.model.CarePlanType;
import com.example.spring.wechat.care.model.CarePlanVersion;
import com.example.spring.wechat.care.model.CareTaskScheduleType;
import com.example.spring.wechat.care.model.CareTaskTemplate;
import com.example.spring.wechat.care.model.CareTaskType;
import com.example.spring.wechat.care.model.MedicalRole;
import com.example.spring.wechat.care.repository.CarePlanRepository;
import com.example.spring.wechat.care.repository.CareTaskRepository;
import com.example.spring.wechat.reminder.config.ReminderProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CarePlanServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    @Test
    void familyDraftIsVersionedAndRequiresClinicalReview() {
        CarePlanRepository plans = mock(CarePlanRepository.class);
        CareTaskRepository tasks = mock(CareTaskRepository.class);
        CareAuthorizationService authorization = mock(CareAuthorizationService.class);
        CarePlanService service = service(plans, tasks, authorization);
        CareActor family = new CareActor(2L, "FAM-2", "家属", MedicalRole.FAMILY);
        when(plans.findByIdempotencyKey("care-plan:1:draft-1")).thenReturn(Optional.empty());
        when(plans.create(any(), any(), anyList())).thenAnswer(invocation -> {
            CarePlan input = invocation.getArgument(0);
            return new CarePlanDetails(input, invocation.getArgument(1), invocation.getArgument(2));
        });

        CarePlanDetails created = service.create(family, 1L, new CarePlanService.CreateCommand(
                "DAILY_CHECKIN", "每日状态计划", "每日了解状态", "按既定问题完成签到",
                LocalDate.of(2026, 7, 29), null, "Asia/Shanghai",
                List.of(new CarePlanService.TaskCommand(
                        "DAILY_CHECKIN", "每日签到", "完成当天签到", "DAILY", LocalTime.of(20, 0),
                        null, null, null, null, 30, 90)), "draft-1"), "request-1");

        assertThat(created.plan().status()).isEqualTo(CarePlanStatus.DRAFT);
        assertThat(created.plan().clinicalReviewRequired()).isTrue();
        assertThat(created.version().revision()).isEqualTo(1);
        assertThat(created.tasks()).hasSize(1);
        assertThat(created.tasks().get(0).timezone()).isEqualTo("Asia/Shanghai");
        verify(authorization).require(family, 1L, CarePermissions.PLAN_MANAGE,
                "CREATE_CARE_PLAN", "CARE_PLAN", null, "request-1");
    }

    @Test
    void medicationTaskCannotBypassDoctorReviewThroughCustomPlan() {
        CarePlanRepository plans = mock(CarePlanRepository.class);
        CareTaskRepository tasks = mock(CareTaskRepository.class);
        CareAuthorizationService authorization = mock(CareAuthorizationService.class);
        CarePlanService service = service(plans, tasks, authorization);
        CareActor nurse = new CareActor(3L, "NUR-3", "护士", MedicalRole.NURSE);
        CarePlan plan = plan(CarePlanType.CUSTOM, CarePlanStatus.WAITING_REVIEW);
        when(plans.findById(8L)).thenReturn(Optional.of(plan));
        when(plans.findDetails(8L)).thenReturn(Optional.of(new CarePlanDetails(
                plan,
                new CarePlanVersion(7L, 8L, 1, "", "", LocalDate.of(2026, 7, 29),
                        null, "Asia/Shanghai", 2L, NOW),
                List.of(new CareTaskTemplate(
                        6L, 7L, 8L, 1L, CareTaskType.MEDICATION_CONFIRMATION, "服药确认", "",
                        CareTaskScheduleType.DAILY, LocalTime.of(20, 0), null, null,
                        LocalDate.of(2026, 7, 29), null, 30, 90, true, 0,
                        "Asia/Shanghai", NOW)))));

        assertThatThrownBy(() -> service.review(nurse, 8L,
                new CarePlanService.ReviewCommand("APPROVE", "", 0L, "request-2")))
                .isInstanceOf(CareException.class)
                .extracting(exception -> ((CareException) exception).code())
                .isEqualTo(CareErrorCode.FORBIDDEN);
        verify(plans, never()).review(anyLong(), anyLong(), anyLong(),
                anyBoolean(), any(String.class), any(Instant.class));
    }

    @Test
    void resumingPlanReactivatesOnlyRepositorySelectedFutureTasks() {
        CarePlanRepository plans = mock(CarePlanRepository.class);
        CareTaskRepository tasks = mock(CareTaskRepository.class);
        CareAuthorizationService authorization = mock(CareAuthorizationService.class);
        CarePlanService service = service(plans, tasks, authorization);
        CareActor doctor = new CareActor(4L, "DOC-4", "医生", MedicalRole.DOCTOR);
        CarePlan paused = plan(CarePlanType.REHABILITATION, CarePlanStatus.PAUSED);
        CarePlan active = new CarePlan(
                paused.id(), paused.patientUserId(), paused.planType(), paused.title(), CarePlanStatus.ACTIVE,
                paused.clinicalReviewRequired(), paused.currentRevision(), paused.createdByUserId(),
                paused.submittedAt(), paused.reviewedByUserId(), paused.reviewedAt(), paused.reviewNote(),
                paused.activatedAt(), paused.endedAt(), paused.idempotencyKey(), 1L, paused.createdAt(), NOW);
        when(plans.findById(8L)).thenReturn(Optional.of(paused), Optional.of(active));
        when(plans.resume(8L, 0L, NOW)).thenReturn(true);

        CarePlan result = service.resume(
                doctor, 8L, new CarePlanService.VersionCommand(0L, "request-5"));

        assertThat(result.status()).isEqualTo(CarePlanStatus.ACTIVE);
        verify(tasks).reactivateFutureCancelledForPlan(8L, 4L, NOW);
    }

    private CarePlanService service(
            CarePlanRepository plans,
            CareTaskRepository tasks,
            CareAuthorizationService authorization) {
        return new CarePlanService(
                plans, tasks, authorization, new ReminderProperties(null, null, "Asia/Shanghai"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CarePlan plan(CarePlanType type, CarePlanStatus status) {
        return new CarePlan(
                8L, 1L, type, "测试计划", status, true, 1, 2L,
                NOW, null, null, "", null, null, "plan-8", 0L, NOW, NOW);
    }
}
