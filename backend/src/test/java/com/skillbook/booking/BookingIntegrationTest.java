package com.skillbook.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbook.availability.AvailabilityRuleRepository;
import com.skillbook.availability.AvailabilitySlot;
import com.skillbook.availability.AvailabilitySlotRepository;
import com.skillbook.availability.AvailabilitySlotStatus;
import com.skillbook.skill.SkillOfferedRepository;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private AvailabilityRuleRepository availabilityRuleRepository;

    @Autowired
    private SkillOfferedRepository skillOfferedRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        availabilitySlotRepository.deleteAll();
        availabilityRuleRepository.deleteAll();
        skillOfferedRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndLogin(String email, String name, String role) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "email": "%s",
                                  "password": "password123",
                                  "role": "%s"
                                }
                                """.formatted(name, email, role)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn();

        JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private String tutorToken() throws Exception {
        return registerAndLogin("alice@example.com", "Tutor Alice", "TUTOR");
    }

    private String studentToken() throws Exception {
        return registerAndLogin("bob@example.com", "Student Bob", "STUDENT");
    }

    private String secondStudentToken() throws Exception {
        return registerAndLogin("carol@example.com", "Student Carol", "STUDENT");
    }

    private void setHourlyRate(String token) throws Exception {
        mockMvc.perform(put("/tutors/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hourlyRate": 40.00
                                }
                                """))
                .andExpect(status().isOk());
    }

    private UUID createSlot(String token, String startIso, String endIso) throws Exception {
        String body = objectMapper.readTree(mockMvc.perform(post("/tutors/availability/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """.formatted(startIso, endIso)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();
        return UUID.fromString(body);
    }

    private UUID createBooking(String token, UUID slotId) throws Exception {
        String body = objectMapper.readTree(mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "%s"
                                }
                                """.formatted(slotId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();
        return UUID.fromString(body);
    }

    private String nowPlusHoursIso(int hours) {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(hours)
                .withMinute(0).withSecond(0).withNano(0)
                .toInstant().toString();
    }

    private UUID studentId(String email) {
        return userRepository.findByEmail(email).get().getId();
    }

    private UUID registerTutorWithRateGetId() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        return studentId("alice@example.com");
    }

    @Test
    void createBookingConfirmsAndMarksSlotBooked() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();

        String startIso = nowPlusHoursIso(30);
        String endIso = OffsetDateTime.parse(startIso).plusHours(1).toInstant().toString();
        UUID slotId = createSlot(aliceToken, startIso, endIso);

        mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "%s",
                                  "message": "Looking forward to it!"
                                }
                                """.formatted(slotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.message").value("Looking forward to it!"))
                .andExpect(jsonPath("$.data.amount").value(40.00))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.slot.id").value(slotId.toString()));

        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId).orElseThrow();
        assertThat(slot.getStatus()).isEqualTo(AvailabilitySlotStatus.BOOKED);
    }

    @Test
    void doubleBookSameSlotRejected() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();

        String startIso = nowPlusHoursIso(30);
        UUID slotId = createSlot(aliceToken, startIso, nowPlusHoursIso(31));

        createBooking(bobToken, slotId);

        mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "%s"
                                }
                                """.formatted(slotId)))
                .andExpect(status().isConflict());
    }

    @Test
    void missingSlotReturns404() throws Exception {
        String bobToken = studentToken();

        mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void tutorCannotCreateBooking() throws Exception {
        String aliceToken = tutorToken();

        mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateBooking() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listMyBookingsReturnsOwnOnly() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();
        String carolToken = secondStudentToken();

        UUID slotA = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID slotB = createSlot(aliceToken, nowPlusHoursIso(33), nowPlusHoursIso(34));
        UUID bookingA = createBooking(bobToken, slotA);
        createBooking(carolToken, slotB);

        mockMvc.perform(get("/bookings")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(bookingA.toString()))
                .andExpect(jsonPath("$.data[0].slot.id").value(slotA.toString()));
    }

    @Test
    void getBookingByIdReturnsOwn() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(get("/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutor.name").value("Tutor Alice"))
                .andExpect(jsonPath("$.data.student.name").value("Student Bob"))
                .andExpect(jsonPath("$.data.slot.id").value(slotId.toString()));
    }

    @Test
    void getBookingByIdNonOwnerReturns404() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();
        String carolToken = secondStudentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(get("/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelBookingBeyond24hFullRefundAndFreesSlot() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        UUID aliceUserId = studentId("alice@example.com");
        String bobToken = studentToken();

        String startIso = nowPlusHoursIso(30);
        String endIso = OffsetDateTime.parse(startIso).plusHours(1).toInstant().toString();
        UUID slotId = createSlot(aliceToken, startIso, endIso);
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationFee").value(0.0))
                .andExpect(jsonPath("$.data.refundAmount").value(40.00));

        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId).orElseThrow();
        assertThat(slot.getStatus()).isEqualTo(AvailabilitySlotStatus.AVAILABLE);

        String from = OffsetDateTime.parse(startIso).minusHours(1).toInstant().toString();
        String to = OffsetDateTime.parse(endIso).plusHours(1).toInstant().toString();
        mockMvc.perform(get("/tutors/" + aliceUserId + "/availability")
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void cancelBookingWithin24hPartialRefund() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();

        String startIso = nowPlusHoursIso(12);
        UUID slotId = createSlot(aliceToken, startIso, nowPlusHoursIso(13));
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationFee").value(20.00))
                .andExpect(jsonPath("$.data.refundAmount").value(20.00));
    }

    @Test
    void cancelBookingAfterSlotStartRejected() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();

        String startIso = nowPlusHoursIso(-5);
        String endIso = nowPlusHoursIso(-4);
        UUID slotId = createSlot(aliceToken, startIso, endIso);

        User bob = userRepository.findByEmail("bob@example.com").get();
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId).orElseThrow();
        Booking planted = bookingRepository.saveAndFlush(
                new Booking(bob, slot, "planted", new BigDecimal("40.00")));

        mockMvc.perform(post("/bookings/" + planted.getId() + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelBookingTwiceRejected() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelCompletedBookingRejected() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();

        String startIso = nowPlusHoursIso(30);
        UUID slotId = createSlot(aliceToken, startIso, nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bookingRepository.save(booking);

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isConflict());
    }

    @Test
    void nonOwnerCannotCancelBooking() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();
        String carolToken = secondStudentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void tutorCannotCancelBooking() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void completedTransitionWhenSessionPasses() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();

        String startIso = nowPlusHoursIso(-3);
        String endIso = nowPlusHoursIso(-2);
        UUID slotId = createSlot(aliceToken, startIso, endIso);

        User bob = userRepository.findByEmail("bob@example.com").get();
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId).orElseThrow();
        Booking planted = bookingRepository.saveAndFlush(
                new Booking(bob, slot, "past session", new BigDecimal("40.00")));

        bookingService.completeExpiredBookings();

        mockMvc.perform(get("/bookings/" + planted.getId())
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(bookingRepository.findById(planted.getId()).get().getCompletedAt()).isNotNull();
    }

    @Test
    void uniqueSlotConstraintBackstop() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken);
        String bobToken = studentToken();
        String carolToken = secondStudentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));

        User bob = userRepository.findByEmail("bob@example.com").get();
        User carol = userRepository.findByEmail("carol@example.com").get();
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId).orElseThrow();

        bookingRepository.saveAndFlush(new Booking(bob, slot, "first", new BigDecimal("40.00")));

        assertThatThrownBy(() -> bookingRepository.saveAndFlush(
                new Booking(carol, slot, "second", new BigDecimal("40.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}