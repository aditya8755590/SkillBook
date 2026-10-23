package com.skillbook.availability;

import com.skillbook.availability.dto.AvailabilityRuleRequest;
import com.skillbook.availability.dto.AvailabilityRuleResponse;
import com.skillbook.availability.dto.AvailabilityRulesRequest;
import com.skillbook.availability.dto.AvailabilitySlotResponse;
import com.skillbook.availability.dto.OneOffSlotRequest;
import com.skillbook.availability.dto.SlotGenerationRequest;
import com.skillbook.availability.dto.SlotGenerationResponse;
import com.skillbook.common.Role;
import com.skillbook.exception.ResourceNotFoundException;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final UserRepository userRepository;
    private final AvailabilityRuleRepository ruleRepository;
    private final AvailabilitySlotRepository slotRepository;

    @Transactional
    public List<AvailabilityRuleResponse> replaceRules(String email, AvailabilityRulesRequest request) {
        User user = findTutorByEmail(email);

        ruleRepository.deleteByTutorId(user.getId());
        ruleRepository.flush();

        long distinctRules = request.rules().stream()
                .map(r -> r.dayOfWeek() + ":" + r.startTime())
                .distinct()
                .count();
        if (distinctRules != request.rules().size()) {
                throw new IllegalArgumentException(
                        "Duplicate rule: the same day and start time cannot be saved twice");
        }

        List<AvailabilityRule> saved = request.rules().stream()
                .map(r -> {
                    DayOfWeek dow = DayOfWeek.of(r.dayOfWeek());
                    LocalTime start = LocalTime.parse(r.startTime());
                    LocalTime end = LocalTime.parse(r.endTime());
                    if (!end.isAfter(start)) {
                        throw new IllegalArgumentException("endTime must be after startTime");
                    }
                    try {
                        ZoneId.of(r.timezone());
                    } catch (java.time.zone.ZoneRulesException e) {
                        throw new IllegalArgumentException("Invalid timezone: " + r.timezone());
                    }
                    return new AvailabilityRule(user, dow, start, end, r.timezone());
                })
                .map(ruleRepository::save)
                .toList();

        return saved.stream()
                .map(AvailabilityRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> listRules(String email) {
        User user = findTutorByEmail(email);
        return ruleRepository.findByTutorIdOrderByDayOfWeekAscStartTimeAsc(user.getId()).stream()
                .map(AvailabilityRuleResponse::from)
                .toList();
    }

    @Transactional
    public AvailabilitySlotResponse addOneOffSlot(String email, OneOffSlotRequest request) {
        User user = findTutorByEmail(email);
        OffsetDateTime startAt = Instant.parse(request.startAt()).atOffset(ZoneOffset.UTC);
        OffsetDateTime endAt = Instant.parse(request.endAt()).atOffset(ZoneOffset.UTC);
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }
        if (slotRepository.existsByTutorIdAndStartAt(user.getId(), startAt)) {
            throw new IllegalStateException("A slot already exists at " + startAt);
        }

        AvailabilitySlot slot = new AvailabilitySlot(user, startAt, endAt, AvailabilitySlotSource.ONE_OFF, null);
        slot = slotRepository.save(slot);
        return AvailabilitySlotResponse.from(slot);
    }

    @Transactional
    public void deleteOneOffSlot(String email, UUID slotId) {
        User user = findTutorByEmail(email);
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("AvailabilitySlot", "id", slotId));
        if (!slot.getTutor().getId().equals(user.getId())) {
            throw new IllegalStateException("Slot not owned by this tutor");
        }
        if (slot.getStatus() == AvailabilitySlotStatus.BOOKED) {
            throw new IllegalStateException("Cannot delete a booked slot");
        }
        slotRepository.delete(slot);
    }

    @Transactional
    public SlotGenerationResponse generateSlots(String email, SlotGenerationRequest request) {
        User user = findTutorByEmail(email);
        UUID tutorId = user.getId();

        LocalDate startDate = LocalDate.parse(request.startDate());
        LocalDate endDate = LocalDate.parse(request.endDate());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<AvailabilityRule> activeRules = ruleRepository.findByTutorIdAndActiveTrue(tutorId);

        int generated = 0;
        int skipped = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();

            for (AvailabilityRule rule : activeRules) {
                if (rule.getDayOfWeek() != dow) {
                    continue;
                }

                ZoneId zone = ZoneId.of(rule.getTimezone());
                OffsetDateTime startUtc = date.atTime(rule.getStartTime()).atZone(zone).toOffsetDateTime()
                        .withOffsetSameInstant(ZoneOffset.UTC);
                OffsetDateTime endUtc = date.atTime(rule.getEndTime()).atZone(zone).toOffsetDateTime()
                        .withOffsetSameInstant(ZoneOffset.UTC);

                if (startUtc.isBefore(now)) {
                    skipped++;
                    continue;
                }
                if (slotRepository.existsByTutorIdAndStartAt(tutorId, startUtc)) {
                    skipped++;
                    continue;
                }

                slotRepository.save(new AvailabilitySlot(user, startUtc, endUtc, AvailabilitySlotSource.RULE, rule));
                generated++;
            }
        }

        return new SlotGenerationResponse(generated, skipped, request.startDate(), request.endDate());
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> listTutorAvailability(UUID tutorUserId, String from, String to) {
        User tutor = userRepository.findById(tutorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", tutorUserId));
        if (tutor.getRole() != Role.TUTOR) {
            throw new ResourceNotFoundException("User", "id", tutorUserId);
        }

        OffsetDateTime fromInstant = Instant.parse(from).atOffset(ZoneOffset.UTC);
        OffsetDateTime toInstant = Instant.parse(to).atOffset(ZoneOffset.UTC);

        return slotRepository.findByTutorIdAndStatusAndStartAtGreaterThanEqualAndStartAtLessThanEqualOrderByStartAtAsc(
                        tutorUserId, AvailabilitySlotStatus.AVAILABLE, fromInstant, toInstant)
                .stream()
                .map(AvailabilitySlotResponse::from)
                .toList();
    }

    private User findTutorByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        if (user.getRole() != Role.TUTOR) {
            throw new IllegalArgumentException("Only tutors can manage availability");
        }
        return user;
    }
}