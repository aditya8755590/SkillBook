package com.skillbook.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TutorProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.skillbook.booking.BookingRepository bookingRepository;

    @Autowired
    private com.skillbook.payment.PaymentRepository paymentRepository;

    @Autowired
    private com.skillbook.availability.AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private com.skillbook.availability.AvailabilityRuleRepository availabilityRuleRepository;

    @Autowired
    private com.skillbook.skill.SkillOfferedRepository skillOfferedRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
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
                        """)).andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "alice@example.com",
                          "password": "password123"
                        }
                        """)).andExpect(status().isOk()).andReturn();

        JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    @Test
    void upsertAndRetrieveOwnTutorProfile() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(put("/tutors/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Experienced math tutor",
                                  "experienceYears": 5,
                                  "hourlyRate": 45.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Experienced math tutor"))
                .andExpect(jsonPath("$.data.experienceYears").value(5))
                .andExpect(jsonPath("$.data.hourlyRate").value(45.00))
                .andExpect(jsonPath("$.data.rating").value(0.0))
                .andExpect(jsonPath("$.data.name").value("Tutor Alice"))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));

        mockMvc.perform(get("/tutors/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Experienced math tutor"));
    }

    @Test
    void ownProfileWithoutExistingReturnsOkWithNoData() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(get("/tutors/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void updateExistingTutorProfile() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(put("/tutors/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Math tutor",
                                  "experienceYears": 3,
                                  "hourlyRate": 30.00
                                }
                                """)).andExpect(status().isOk());

        mockMvc.perform(put("/tutors/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Advanced math tutor",
                                  "experienceYears": 7,
                                  "hourlyRate": 55.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Advanced math tutor"))
                .andExpect(jsonPath("$.data.experienceYears").value(7))
                .andExpect(jsonPath("$.data.hourlyRate").value(55.00));
    }

    @Test
    void tutorCannotUpdateStudentProfile() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(put("/students/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Hello",
                                  "educationLevel": "BS"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTutorProfileByUserId() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(put("/tutors/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Physics tutor",
                                  "experienceYears": 10,
                                  "hourlyRate": 60.00
                                }
                                """)).andExpect(status().isOk());

        UUID userId = userRepository.findByEmail("alice@example.com").get().getId();

        mockMvc.perform(get("/tutors/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Physics tutor"))
                .andExpect(jsonPath("$.data.name").value("Tutor Alice"));
    }

    @Test
    void getTutorProfileByUserIdIncludesOfferedSkills() throws Exception {
        String token = registerAndLoginTutor();

        mockMvc.perform(put("/tutors/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Java tutor",
                                  "experienceYears": 6,
                                  "hourlyRate": 40.00
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/tutors/profile/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": ["Java", "Spring Boot"]
                                }
                                """))
                .andExpect(status().isOk());

        UUID userId = userRepository.findByEmail("alice@example.com").get().getId();

        mockMvc.perform(get("/tutors/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Tutor Alice"))
                .andExpect(jsonPath("$.data.skills.length()").value(2))
                .andExpect(jsonPath("$.data.skills[0].name").value("Java"))
                .andExpect(jsonPath("$.data.skills[1].name").value("Spring Boot"));
    }

    @Test
    void validationRejectsInvalidPayload() throws Exception {
        String token = registerAndLoginTutor();
        String longBio = "x".repeat(2001);

        mockMvc.perform(put("/tutors/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "%s",
                                  "experienceYears": -1,
                                  "hourlyRate": -5.00
                                }
                                """.formatted(longBio)))
                .andExpect(status().isBadRequest());
    }
}