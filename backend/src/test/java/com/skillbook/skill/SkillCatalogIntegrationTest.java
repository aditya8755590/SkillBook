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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillOfferedRepository skillOfferedRepository;

    @Autowired
    private SkillWantedRepository skillWantedRepository;

    @BeforeEach
    void setUp() {
        skillWantedRepository.deleteAll();
        skillOfferedRepository.deleteAll();
        skillRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetToken(String email, String role) throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "%s User",
                          "email": "%s",
                          "password": "password123",
                          "role": "%s"
                        }
                        """.formatted(role, email, role)))
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

    @Test
    void getSkillsEmptyInitially() throws Exception {
        mockMvc.perform(get("/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void postCreatesSkill() throws Exception {
        String token = registerAndGetToken("alice@test.com", "TUTOR");

        mockMvc.perform(post("/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mathematics",
                                  "category": "STEM"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Mathematics"))
                .andExpect(jsonPath("$.data.category").value("STEM"))
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void postGetOrCreateIsIdempotent() throws Exception {
        String token = registerAndGetToken("alice@test.com", "TUTOR");

        MvcResult first = mockMvc.perform(post("/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mathematics",
                                  "category": "STEM"
                                }
                                """))
                .andExpect(status().isCreated()).andReturn();

        String id1 = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mathematics",
                                  "category": "STEM"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Mathematics"));

        String id2 = objectMapper.readTree(
                mockMvc.perform(post("/skills")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Mathematics"
                                        }
                                        """))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();

        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void postRejectsBlankName() throws Exception {
        String token = registerAndGetToken("alice@test.com", "TUTOR");

        mockMvc.perform(post("/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "category": "STEM"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mathematics"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllSortedByName() throws Exception {
        String token = registerAndGetToken("alice@test.com", "TUTOR");

        mockMvc.perform(post("/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Physics",
                                  "category": "STEM"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Algebra",
                                  "category": "Math"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Chemistry",
                                  "category": "STEM"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("Algebra"))
                .andExpect(jsonPath("$.data[1].name").value("Chemistry"))
                .andExpect(jsonPath("$.data[2].name").value("Physics"));
    }
}
