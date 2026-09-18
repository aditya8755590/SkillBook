package com.skillbook.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbook.availability.AvailabilityRuleRepository;
import com.skillbook.availability.AvailabilitySlotRepository;
import com.skillbook.booking.BookingRepository;
import com.skillbook.skill.SkillOfferedRepository;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTest {

    private static final String SESSION_ID = "cs_test_123";
    private static final String PAYMENT_INTENT_ID = "pi_test_123";
    private static final String CHECKOUT_URL = "https://checkout.stripe.com/test-session";
    private static final String REFUND_ID = "re_test_123";

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

    @MockBean
    private StripeGateway stripeGateway;

    @BeforeEach
    void setUp() {
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

    private String nowPlusHoursIso(int hours) {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(hours)
                .withMinute(0).withSecond(0).withNano(0)
                .toInstant().toString();
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

    private void stubCheckoutSession() {
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeGateway.CheckoutSessionResult(CHECKOUT_URL, SESSION_ID));
    }

    private void stubRefund() {
        when(stripeGateway.refund(any()))
                .thenReturn(new StripeGateway.RefundResult(REFUND_ID));
    }

    private void sendWebhook(String eventType, String sessionId, String paymentIntentId) throws Exception {
        when(stripeGateway.constructEvent(anyString(), anyString()))
                .thenReturn(new StripeGateway.WebhookEvent(
                        eventType, sessionId, paymentIntentId, Map.of("bookingId", "x")));
        mockMvc.perform(post("/payment/webhook")
                        .header("Stripe-Signature", "t=123,v1=fake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    /** Creates tutor+student, a slot `hoursFromNow` out, a booking, checks out and completes payment. */
    private UUID bookedAndPaid(int hoursFromNow) throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(hoursFromNow), nowPlusHoursIso(hoursFromNow + 1));
        UUID bookingId = createBooking(bobToken, slotId);
        stubCheckoutSession();
        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkoutUrl").value(CHECKOUT_URL))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        sendWebhook("checkout.session.completed", SESSION_ID, PAYMENT_INTENT_ID);
        return bookingId;
    }

    // ---------- checkouts ----------

    @Test
    void checkoutReturnsStripeUrlAndPaymentAppearsOnBooking() throws Exception {
        UUID bookingId = bookedAndPaid(30);
        String bobToken = login("bob@example.com");

        mockMvc.perform(get("/bookings" )
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].payment.status").value("PAID"))
                .andExpect(jsonPath("$.data[0].payment.amount").value(40.00));

        Payment payment = paymentRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getStripePaymentIntentId()).isEqualTo(PAYMENT_INTENT_ID);
        assertThat(payment.getStripeCheckoutSessionId()).isEqualTo(SESSION_ID);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    void nonOwnerCannotStartCheckout() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();
        String carolToken = secondStudentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void tutorCannotStartCheckout() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotStartCheckout() throws Exception {
        mockMvc.perform(post("/bookings/" + UUID.randomUUID() + "/checkout"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- webhooks ----------

    @Test
    void duplicateCompletedWebhookIsIdempotent() throws Exception {
        bookedAndPaid(30);
        sendWebhook("checkout.session.completed", SESSION_ID, PAYMENT_INTENT_ID);
        sendWebhook("checkout.session.completed", SESSION_ID, PAYMENT_INTENT_ID);

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.findAll().get(0).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void expiredWebhookMarksPaymentFailed() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);
        stubCheckoutSession();
        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());
        sendWebhook("checkout.session.expired", SESSION_ID, PAYMENT_INTENT_ID);

        Payment payment = paymentRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void unknownSessionInWebhookIsIgnored() throws Exception {
        sendWebhook("checkout.session.completed", "cs_unknown", "pi_unknown");
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void invalidSignatureRejected() throws Exception {
        when(stripeGateway.constructEvent(anyString(), anyString()))
                .thenThrow(new PaymentSignatureException("bad signature", new RuntimeException()));
        mockMvc.perform(post("/payment/webhook")
                        .header("Stripe-Signature", "t=0,v1=bogus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- refunds ----------

    @Test
    void cancelPaidBookingRefundsPartialAmount() throws Exception {
        UUID bookingId = bookedAndPaid(12);
        stubRefund();
        String bobToken = login("bob@example.com");

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        Payment payment = paymentRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("20.00");
        assertThat(payment.getStripeRefundId()).isEqualTo(REFUND_ID);
        assertThat(payment.getRefundedAt()).isNotNull();
    }

    @Test
    void cancelBeforePaymentDoesNotCallGateway() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());

        verify(stripeGateway, never()).refund(any());
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void completedWebhookForCancelledWhilePayingRefundsFullAmount() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);
        stubCheckoutSession();
        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());
        stubRefund();
        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());
        sendWebhook("checkout.session.completed", SESSION_ID, PAYMENT_INTENT_ID);

        Payment payment = paymentRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("40.00");
        assertThat(payment.getStripeRefundId()).isEqualTo(REFUND_ID);
    }

    @Test
    void refundUsesExpectedCents() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(12), nowPlusHoursIso(13));
        UUID bookingId = createBooking(bobToken, slotId);
        stubCheckoutSession();
        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());
        sendWebhook("checkout.session.completed", SESSION_ID, PAYMENT_INTENT_ID);
        stubRefund();
        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());

        verify(stripeGateway).refund(argThat(r -> r.amountCents() == 2000L));
        verify(stripeGateway).refund(argThat(r -> r.paymentIntentId().equals(PAYMENT_INTENT_ID)));
    }

    // ---------- free sessions ----------

    @Test
    void freeSessionSkippedCheckoutMarkedPaid() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 0.00);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkoutUrl").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        verify(stripeGateway, never()).createCheckoutSession(any());
        verify(stripeGateway, never()).constructEvent(anyString(), anyString());
    }

    // ---------- state edges ----------

    @Test
    void retryAfterFailedExpiryCreatesNewSession() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        stubCheckoutSession();
        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());
        sendWebhook("checkout.session.expired", SESSION_ID, PAYMENT_INTENT_ID);

        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeGateway.CheckoutSessionResult(CHECKOUT_URL, "cs_test_retry"));
        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());

        Payment payment = paymentRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getStripeCheckoutSessionId()).isEqualTo("cs_test_retry");
    }

    @Test
    void checkoutAfterPaidRejected() throws Exception {
        UUID bookingId = bookedAndPaid(30);
        String bobToken = login("bob@example.com");

        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isConflict());
    }

    @Test
    void checkoutAfterRefundedRejected() throws Exception {
        UUID bookingId = bookedAndPaid(12);
        stubRefund();
        String bobToken = login("bob@example.com");
        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isConflict());
    }

    @Test
    void gatewayFailureReturns502() throws Exception {
        String aliceToken = tutorToken();
        setHourlyRate(aliceToken, 40.00);
        String bobToken = studentToken();

        UUID slotId = createSlot(aliceToken, nowPlusHoursIso(30), nowPlusHoursIso(31));
        UUID bookingId = createBooking(bobToken, slotId);

        when(stripeGateway.createCheckoutSession(any()))
                .thenThrow(new PaymentGatewayException("stripe down", new RuntimeException()));

        mockMvc.perform(post("/bookings/" + bookingId + "/checkout")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isBadGateway());
    }
}