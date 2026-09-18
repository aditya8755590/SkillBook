package com.skillbook.skill;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillOfferedRepository skillOfferedRepository;

    @Autowired
    private SkillWantedRepository skillWantedRepository;

    @Autowired
    private com.skillbook.booking.BookingRepository bookingRepository;

    @Autowired
    private com.skillbook.availability.AvailabilitySlotRepository availabilitySlotRepository;

    @Autowired
    private com.skillbook.availability.AvailabilityRuleRepository availabilityRuleRepository;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        availabilitySlotRepository.deleteAll();
        availabilityRuleRepository.deleteAll();
        skillWantedRepository.deleteAll();
        skillOfferedRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetToken(String email, String name, String role) throws Exception {
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

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "password123"
                        }
                        """.formatted(email)))
                .andExpect(status().isOk()).andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private void createTutor(String token, String bio, int rate, String... skills) throws Exception {
        mockMvc.perform(put("/tutors/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "%s",
                                  "experienceYears": 5,
                                  "hourlyRate": %d.00
                                }
                                """.formatted(bio, rate)))
                .andExpect(status().isOk());

        StringBuilder skillsJson = new StringBuilder("[");
        for (int i = 0; i < skills.length; i++) {
            if (i > 0) skillsJson.append(", ");
            skillsJson.append("\"").append(skills[i]).append("\"");
        }
        skillsJson.append("]");

        mockMvc.perform(put("/tutors/profile/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": %s
                                }
                                """.formatted(skillsJson.toString())))
                .andExpect(status().isOk());
    }

    @Test
    void searchByNameFragment() throws Exception {
        String token1 = registerAndGetToken("alice@test.com", "Alice Math", "TUTOR");
        String token2 = registerAndGetToken("bob@test.com", "Bob Physics", "TUTOR");

        createTutor(token1, "Math specialist", 40, "Algebra");
        createTutor(token2, "Physics expert", 60, "Mechanics");

        mockMvc.perform(get("/tutors?q=alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Alice Math"));
    }

    @Test
    void searchBySkillName() throws Exception {
        String token1 = registerAndGetToken("alice@test.com", "Alice", "TUTOR");
        String token2 = registerAndGetToken("bob@test.com", "Bob", "TUTOR");

        createTutor(token1, "Math expert", 40, "Algebra", "Calculus");
        createTutor(token2, "Physics expert", 50, "Mechanics");

        mockMvc.perform(get("/tutors?skill=Calculus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Alice"));
    }

    @Test
    void filterByPriceRange() throws Exception {
        String token1 = registerAndGetToken("alice@test.com", "Alice", "TUTOR");
        String token2 = registerAndGetToken("bob@test.com", "Bob", "TUTOR");

        createTutor(token1, "Affordable tutor", 30, "Math");
        createTutor(token2, "Premium tutor", 80, "Math");

        mockMvc.perform(get("/tutors?minRate=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Bob"));

        mockMvc.perform(get("/tutors?maxRate=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Alice"));
    }

    @Test
    void filterByMinRating() throws Exception {
        String token1 = registerAndGetToken("alice@test.com", "Alice", "TUTOR");
        String token2 = registerAndGetToken("bob@test.com", "Bob", "TUTOR");

        createTutor(token1, "Top rated", 50, "Math");
        createTutor(token2, "New tutor", 50, "Math");

        // Update rating directly via repository (simulating prior reviews)
        com.skillbook.user.User alice = userRepository.findByEmail("alice@test.com").orElseThrow();
        alice.getTutorProfile().setRating(new java.math.BigDecimal("4.8"));

        com.skillbook.user.User bob = userRepository.findByEmail("bob@test.com").orElseThrow();
        bob.getTutorProfile().setRating(new java.math.BigDecimal("3.5"));
        userRepository.saveAll(java.util.List.of(alice, bob));

        mockMvc.perform(get("/tutors?minRating=4.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Alice"));
    }

    @Test
    void sortByPriceAsc() throws Exception {
        String token1 = registerAndGetToken("alice@test.com", "Alice", "TUTOR");
        String token2 = registerAndGetToken("bob@test.com", "Bob", "TUTOR");

        createTutor(token1, "Tutor A", 80, "Math");
        createTutor(token2, "Tutor B", 30, "Math");

        mockMvc.perform(get("/tutors?sort=price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Bob"))
                .andExpect(jsonPath("$.data.content[1].name").value("Alice"));
    }

    @Test
    void paginateLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            String token = registerAndGetToken("t" + i + "@test.com", "Tutor " + i, "TUTOR");
            createTutor(token, "Bio " + i, 30 + i * 10, "Skill" + i);
        }

        mockMvc.perform(get("/tutors?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3));
    }

    @Test
    void publicAccessWithoutToken() throws Exception {
        String token = registerAndGetToken("alice@test.com", "Alice", "TUTOR");
        createTutor(token, "Public profile", 40, "Math");

        mockMvc.perform(get("/tutors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void ownProfileNotPubliclyExposed() throws Exception {
        String token = registerAndGetToken("alice@test.com", "Alice", "TUTOR");
        createTutor(token, "Profile bio", 40, "Math");

        mockMvc.perform(get("/tutors/profile"))
                .andExpect(status().isUnauthorized());
    }
}
