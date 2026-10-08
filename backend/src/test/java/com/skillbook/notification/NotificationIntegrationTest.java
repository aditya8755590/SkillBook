package com.skillbook.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbook.availability.AvailabilityRuleRepository;
import com.skillbook.availability.AvailabilitySlot;
import com.skillbook.availability.AvailabilitySlotRepository;
import com.skillbook.booking.Booking;
import com.skillbook.booking.BookingRepository;
import com.skillbook.booking.BookingService;
import com.skillbook.booking.BookingStatus;
import com.skillbook.payment.PaymentRepository;
import com.skillbook.payment.PaymentStatus;
import com.skillbook.payment.StripeGateway;
import com.skillbook.skill.SkillOfferedRepository;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    private static final String SESSION_ID = "cs_test_notif";
    private static final String PAYMENT_INTENT_ID = "pi_test_notif";
    private static final String CHECKOUT_URL = "https://checkout.stripe.com/test-session";
    private static final String REFUND_ID = "re_test_notif";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillOfferedRepository skillOfferedRepository;

    @Autowired
    private AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private AvailabilityRuleRepository availabilityRuleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private BookingService bookingService;

    @MockBean
    private StripeGateway stripeGateway;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        availabilitySlotRepository.deleteAll();
        availabilityRuleRepository.deleteAll();
        skillOfferedRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- helpers ----------

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
        return login(email);
    }

    private String login(String email) throws Exception {
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

    private void setHourlyRate(String token, double rate) throws Exception {
        mockMvc.perform(put("/tutors/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hourlyRate": %s
                                }
                                """.formatted(rate)))
                .andExpect(status().isOk());
    }

    private String nowPlusHoursIso(int hours) {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(hours)
                .withMinute(0).withSecond(0).withNano(0)
                .toInstant().toString();
    }

    private UUID createSlot(String token, String startIso, String endIso) throws Exception {
        MvcResult result = mockMvc.perform(post("/tutors/availability/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """.formatted(startIso, endIso)))
                .andExpect(status().isOk()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    private UUID createBooking(String token, UUID slotId) throws Exception {
        MvcResult result = mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "%s"
                                }
                                """.formatted(slotId)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    /** tutor alice (rate 40) + student bob, a booking `hoursFromNow` out. */
    private UUID bookSession(int hoursFromNow) throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();
        UUID slotId = createSlot(aliceToken,
                nowPlusHoursIso(hoursFromNow), nowPlusHoursIso(hoursFromNow + 1));
        return createBooking(bobToken, slotId);
    }

    private JsonNode getNotifications(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private List<String> typesFor(String token) throws Exception {
        JsonNode data = getNotifications(token);
        return data.findValuesAsText("type");
    }

    private void stubCheckoutSession() {
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeGateway.CheckoutSessionResult(CHECKOUT_URL, SESSION_ID));
    }

    private void stubRefund() {
        when(stripeGateway.refund(any()))
                .thenReturn(new StripeGateway.RefundResult(REFUND_ID));
    }

    private void sendWebhook(String eventType) throws Exception {
        when(stripeGateway.constructEvent(anyString(), anyString()))
                .thenReturn(new StripeGateway.WebhookEvent(
                        eventType, SESSION_ID, PAYMENT_INTENT_ID, Map.of("bookingId", "x")));
        mockMvc.perform(post("/payment/webhook")
                        .header("Stripe-Signature", "t=123,v1=fake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private void cancelBooking(String token, UUID bookingId) throws Exception {
        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private int unreadCount(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("count").asInt();
    }

    // ---------- event fan-out ----------

    @Test
    void bookingConfirmationNotifiesStudentAndTutor() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();
        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        JsonNode studentNotifications = getNotifications(bobToken);
        assertThat(studentNotifications.size()).isEqualTo(1);
        assertThat(studentNotifications.get(0).path("type").asText()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(studentNotifications.get(0).path("read").asBoolean()).isFalse();
        assertThat(studentNotifications.get(0).path("bookingId").asText())
                .isEqualTo(bookingId.toString());
        assertThat(studentNotifications.get(0).path("message").asText())
                .contains("Tutor Alice");

        assertThat(typesFor(aliceToken)).contains("NEW_BOOKING");
    }

    @Test
    void cancelNotifiesBothParties() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();
        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        cancelBooking(bobToken, bookingId);

        assertThat(typesFor(bobToken)).contains("BOOKING_CANCELLED");
        assertThat(typesFor(aliceToken)).contains("BOOKING_CANCELLED");
    }

    @Test
    void paymentReceiptNotifiedOnWebhook() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();
        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        stubCheckoutSession();
        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());
        sendWebhook("checkout.session.completed");

        JsonNode studentNotifications = getNotifications(bobToken);
        assertThat(studentNotifications.findValuesAsText("type")).contains("PAYMENT_RECEIPT");
        assertThat(studentNotifications.findValuesAsText("message"))
                .anySatisfy(m -> assertThat(m).contains("40.00"));
        assertThat(paymentRepository.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void refundNotifiedWhenGatewayRefunds() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();
        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);
        stubCheckoutSession();
        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());
        sendWebhook("checkout.session.completed");
        stubRefund();

        cancelBooking(bobToken, bookingId);

        assertThat(typesFor(bobToken)).contains("PAYMENT_REFUNDED");
        assertThat(paymentRepository.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void sessionCompletionNotifiesBothParties() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();
        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(-3), nowPlusHoursIso(-2));

        User bob = userRepository.findByEmail("bob@example.com").get();
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId).orElseThrow();
        Booking planted = bookingRepository.saveAndFlush(
                new Booking(bob, slot, "past session", new BigDecimal("40.00")));

        bookingService.completeExpiredBookings();

        assertThat(bookingRepository.findById(planted.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.COMPLETED);
        assertThat(typesFor(bobToken)).contains("SESSION_COMPLETED");
        assertThat(typesFor(aliceToken)).contains("SESSION_COMPLETED");
    }

    // ---------- reminders ----------

    @Test
    void reminderSentOnceWithin24hWindow() throws Exception {
        bookSession(2);
        String bobToken = login("bob@example.com");
        String aliceToken = login("alice@example.com");

        bookingService.sendSessionReminders();

        assertThat(typesFor(bobToken)).contains("SESSION_REMINDER");
        assertThat(typesFor(aliceToken)).contains("SESSION_REMINDER");
        Booking booking = bookingRepository.findAll().get(0);
        assertThat(booking.getReminderSentAt()).isNotNull();

        int studentCountAfterFirstRun = typesFor(bobToken).size();
        bookingService.sendSessionReminders();
        assertThat(typesFor(bobToken)).hasSize(studentCountAfterFirstRun);
    }

    @Test
    void reminderSkipsCancelledAndFarFutureBookings() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();

        UUID cancelledSlot = createSlot(aliceToken, nowPlusHoursIso(2), nowPlusHoursIso(3));
        UUID cancelledBooking = createBooking(bobToken, cancelledSlot);
        cancelBooking(bobToken, cancelledBooking);

        UUID farSlot = createSlot(aliceToken, nowPlusHoursIso(72), nowPlusHoursIso(73));
        createBooking(bobToken, farSlot);

        bookingService.sendSessionReminders();

        assertThat(typesFor(bobToken)).doesNotContain("SESSION_REMINDER");
        assertThat(typesFor(aliceToken)).doesNotContain("SESSION_REMINDER");
    }

    // ---------- read endpoints ----------

    @Test
    void listReturnsOwnNotificationsNewestFirst() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();
        String carolToken = secondStudentToken();

        UUID firstSlot = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        createBooking(bobToken, firstSlot);
        UUID secondSlot = createSlot(aliceToken, nowPlusHoursIso(32), nowPlusHoursIso(33));
        createBooking(bobToken, secondSlot);

        JsonNode bobNotifications = getNotifications(bobToken);
        assertThat(bobNotifications.size()).isEqualTo(2);
        java.time.LocalDateTime newest = java.time.LocalDateTime.parse(
                bobNotifications.get(0).path("createdAt").asText());
        java.time.LocalDateTime older = java.time.LocalDateTime.parse(
                bobNotifications.get(1).path("createdAt").asText());
        assertThat(newest).isAfter(older);

        assertThat(getNotifications(carolToken)).isEmpty();
    }

    @Test
    void unreadCountReflectsReadState() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();
        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        assertThat(unreadCount(bobToken)).isEqualTo(1);

        UUID notificationId = UUID.fromString(
                getNotifications(bobToken).get(0).path("id").asText());
        mockMvc.perform(post("/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());
        assertThat(unreadCount(bobToken)).isZero();

        cancelBooking(bobToken, bookingId);
        assertThat(unreadCount(bobToken)).isEqualTo(1);

        mockMvc.perform(post("/notifications/read-all")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
        assertThat(unreadCount(bobToken)).isZero();
    }

    @Test
    void markOwnNotificationRead() throws Exception {
        bookSession(30);
        String bobToken = login("bob@example.com");

        UUID notificationId = UUID.fromString(
                getNotifications(bobToken).get(0).path("id").asText());
        mockMvc.perform(post("/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].read").value(true));
    }

    @Test
    void markReadOthersNotificationReturns404() throws Exception {
        bookSession(30);
        String bobToken = login("bob@example.com");
        String carolToken = secondStudentToken();

        UUID bobNotificationId = UUID.fromString(
                getNotifications(bobToken).get(0).path("id").asText());

        mockMvc.perform(post("/notifications/" + bobNotificationId + "/read")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void notificationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/notifications/read-all")).andExpect(status().isUnauthorized());
    }
}
