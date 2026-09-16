package com.skillbook.availability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbook.skill.SkillOfferedRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvailabilityRuleIntegrationTest {

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

    private String registerAndLoginStudent() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Student Bob",
                                  "email": "bob@example.com",
                                  "password": "password123",
                                  "role": "STUDENT"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bob@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk()).andReturn();

        JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    @Test
    void replaceWeeklyRulesAndList() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(put("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 1, "startTime": "09:00", "endTime": "11:30", "timezone": "UTC" },
                                    { "dayOfWeek": 4, "startTime": "13:00", "endTime": "15:00", "timezone": "Asia/Kolkata" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.data[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.data[0].endTime").value("11:30"))
                .andExpect(jsonPath("$.data[0].timezone").value("UTC"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[1].dayOfWeek").value(4));

        mockMvc.perform(get("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void replaceRulesClearsPrevious() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(put("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 1, "startTime": "09:00", "endTime": "10:00", "timezone": "UTC" },
                                    { "dayOfWeek": 4, "startTime": "09:00", "endTime": "10:00", "timezone": "UTC" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 2, "startTime": "10:00", "endTime": "12:00", "timezone": "UTC" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].dayOfWeek").value(2));
    }

    @Test
    void invalidDayOfWeekRejected() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(put("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 0, "startTime": "09:00", "endTime": "10:00", "timezone": "UTC" }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 8, "startTime": "09:00", "endTime": "10:00", "timezone": "UTC" }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidTimeRangeRejected() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(put("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 1, "startTime": "11:00", "endTime": "10:00", "timezone": "UTC" }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidTimezoneRejected() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(put("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 1, "startTime": "09:00", "endTime": "10:00", "timezone": "Not/AZone" }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotManageRules() throws Exception {
        String token = registerAndLoginStudent();

        mockMvc.perform(put("/tutors/availability/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 1, "startTime": "09:00", "endTime": "10:00", "timezone": "UTC" }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotManageRules() throws Exception {
        mockMvc.perform(put("/tutors/availability/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    { "dayOfWeek": 1, "startTime": "09:00", "endTime": "10:00", "timezone": "UTC" }
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}