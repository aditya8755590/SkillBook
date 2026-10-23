package com.skillbook.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentProfileIntegrationTest {

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
                        """)).andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "bob@example.com",
                          "password": "password123"
                        }
                        """)).andExpect(status().isOk()).andReturn();

        JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
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
    void upsertAndRetrieveOwnStudentProfile() throws Exception {
        String token = registerAndLoginStudent();

        mockMvc.perform(put("/students/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Computer science undergrad",
                                  "educationLevel": "Bachelor"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Computer science undergrad"))
                .andExpect(jsonPath("$.data.educationLevel").value("Bachelor"))
                .andExpect(jsonPath("$.data.name").value("Student Bob"))
                .andExpect(jsonPath("$.data.email").value("bob@example.com"));

        mockMvc.perform(get("/students/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Computer science undergrad"));
    }

    @Test
    void ownProfileWithoutExistingReturnsOkWithNoData() throws Exception {
        String token = registerAndLoginStudent();

        mockMvc.perform(get("/students/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void updateExistingStudentProfile() throws Exception {
        String token = registerAndLoginStudent();

        mockMvc.perform(put("/students/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "CS undergrad",
                                  "educationLevel": "BS"
                                }
                                """)).andExpect(status().isOk());

        mockMvc.perform(put("/students/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Senior CS undergrad",
                                  "educationLevel": "BS Computer Science"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Senior CS undergrad"))
                .andExpect(jsonPath("$.data.educationLevel").value("BS Computer Science"));
    }

    @Test
    void studentCannotUpdateTutorProfile() throws Exception {
        String token = registerAndLoginStudent();

        mockMvc.perform(put("/tutors/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Hello",
                                  "experienceYears": 5,
                                  "hourlyRate": 30.00
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}