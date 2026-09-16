package com.skillbook.availability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbook.skill.SkillOfferedRepository;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvailabilitySlotIntegrationTest {

    private static final String MONDAY = "2027-02-01";
    private static final String TUESDAY = "2027-02-02";

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

    @BeforeEach
    void setUp() {
        availabilitySlotRepository.deleteAll();
        availabilityRuleRepository.deleteAll();
        skillOfferedRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndLoginTutor() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Tutor Alice",
                                  "email": "alice@example.com",
                                  "password": "password123",
                                  "role": "TUTOR"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk()).andReturn();

        JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private UUID tutorUserId() {
        return userRepository.findByEmail("alice@example.com").get().getId();
    }

    private void setUpWeeklyRule(String token) throws Exception {
        mockMvc.perform(put("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 1, "startTime": "09:00", "endTime": "11:00", "timezone": "UTC" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void addAndDeleteOneOffSlot() throws Exception {
        String token = registerAndLoginTutor();
        UUID userId = tutorUserId();

        String slotId = objectMapper.readTree(mockMvc.perform(post("/tutors/availability/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2027-02-01T09:00:00Z",
                                  "endAt": "2027-02-01T10:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startAt").value("2027-02-01T09:00:00Z"))
                .andExpect(jsonPath("$.data.endAt").value("2027-02-01T10:00:00Z"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.source").value("ONE_OFF"))
                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(get("/tutors/" + userId + "/availability")
                        .param("from", "2027-02-01T00:00:00Z")
                        .param("to", "2027-02-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/tutors/availability/slots/" + slotId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tutors/" + userId + "/availability")
                        .param("from", "2027-02-01T00:00:00Z")
                        .param("to", "2027-02-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void deleteBookedSlotRejected() throws Exception {
        String token = registerAndLoginTutor();
        UUID userId = tutorUserId();

        String slotId = objectMapper.readTree(mockMvc.perform(post("/tutors/availability/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2027-02-01T09:00:00Z",
                                  "endAt": "2027-02-01T10:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();

        AvailabilitySlot slot = availabilitySlotRepository
                .findById(UUID.fromString(slotId)).orElseThrow();
        slot.setStatus(AvailabilitySlotStatus.BOOKED);
        availabilitySlotRepository.save(slot);

        mockMvc.perform(delete("/tutors/availability/slots/" + slotId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        assertThat(availabilitySlotRepository.findById(UUID.fromString(slotId))).isPresent();
    }

    @Test
    void duplicateSlotAtSameStartRejected() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(post("/tutors/availability/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2027-02-01T09:00:00Z",
                                  "endAt": "2027-02-01T10:00:00Z"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tutors/availability/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2027-02-01T09:00:00Z",
                                  "endAt": "2027-02-01T10:30:00Z"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void generateSlotsFromWeeklyRule() throws Exception {
        String token = registerAndLoginTutor();
        UUID userId = tutorUserId();
        setUpWeeklyRule(token);

        mockMvc.perform(post("/tutors/availability/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "%s",
                                  "endDate": "%s"
                                }
                                """.formatted(MONDAY, TUESDAY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generated").value(1))
                .andExpect(jsonPath("$.data.skipped").value(0));

        mockMvc.perform(get("/tutors/" + userId + "/availability")
                        .param("from", MONDAY + "T00:00:00Z")
                        .param("to", "2027-02-03T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].startAt").value(MONDAY + "T09:00:00Z"))
                .andExpect(jsonPath("$.data[0].endAt").value(MONDAY + "T11:00:00Z"))
                .andExpect(jsonPath("$.data[0].source").value("RULE"));
    }

    @Test
    void generateIsIdempotent() throws Exception {
        String token = registerAndLoginTutor();
        setUpWeeklyRule(token);

        mockMvc.perform(post("/tutors/availability/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "%s",
                                  "endDate": "%s"
                                }
                                """.formatted(MONDAY, MONDAY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generated").value(1));

        mockMvc.perform(post("/tutors/availability/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "%s",
                                  "endDate": "%s"
                                }
                                """.formatted(MONDAY, MONDAY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generated").value(0));
    }

    @Test
    void generateHonorsTimezone() throws Exception {
        String token = registerAndLoginTutor();
        UUID userId = tutorUserId();

        mockMvc.perform(put("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 1, "startTime": "09:00", "endTime": "10:00", "timezone": "Asia/Kolkata" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tutors/availability/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "%s",
                                  "endDate": "%s"
                                }
                                """.formatted(MONDAY, MONDAY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generated").value(1));

        mockMvc.perform(get("/tutors/" + userId + "/availability")
                        .param("from", MONDAY + "T00:00:00Z")
                        .param("to", TUESDAY + "T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].startAt").value(MONDAY + "T03:30:00Z"))
                .andExpect(jsonPath("$.data[0].endAt").value(MONDAY + "T04:30:00Z"));
    }

    @Test
    void publicCanListAvailabilityWithoutToken() throws Exception {
        String token = registerAndLoginTutor();
        UUID userId = tutorUserId();
        setUpWeeklyRule(token);

        mockMvc.perform(post("/tutors/availability/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "%s",
                                  "endDate": "%s"
                                }
                                """.formatted(MONDAY, MONDAY)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tutors/" + userId + "/availability")
                        .param("from", MONDAY + "T00:00:00Z")
                        .param("to", TUESDAY + "T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void unknownTutorReturns404() throws Exception {
        mockMvc.perform(get("/tutors/" + UUID.randomUUID() + "/availability")
                        .param("from", MONDAY + "T00:00:00Z")
                        .param("to", TUESDAY + "T00:00:00Z"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pastDatesSkippedDuringGeneration() throws Exception {
        String token = registerAndLoginTutor();
        setUpWeeklyRule(token);

        mockMvc.perform(post("/tutors/availability/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2020-01-01",
                                  "endDate": "2020-01-07"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generated").value(0));
    }
}