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
class TutorSkillIntegrationTest {

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

    @Test
    void tutorReplacesOfferedSkills() throws Exception {
        String token = registerAndGetToken("alice@test.com", "Tutor Alice", "TUTOR");

        mockMvc.perform(put("/tutors/profile/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": ["Mathematics", "Physics", "Algebra"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get("/tutors/profile/skills")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("Algebra"))
                .andExpect(jsonPath("$.data[1].name").value("Mathematics"))
                .andExpect(jsonPath("$.data[2].name").value("Physics"));
    }

    @Test
    void tutorReplaceClearsSkills() throws Exception {
        String token = registerAndGetToken("alice@test.com", "Tutor Alice", "TUTOR");

        mockMvc.perform(put("/tutors/profile/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": ["Mathematics", "Physics"]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/tutors/profile/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/tutors/profile/skills")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void studentCannotModifyTutorSkills() throws Exception {
        String token = registerAndGetToken("bob@test.com", "Student Bob", "STUDENT");

        mockMvc.perform(put("/tutors/profile/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": ["Mathematics"]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotModifySkills() throws Exception {
        mockMvc.perform(put("/tutors/profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": ["Mathematics"]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void skillGetOrCreateByName() throws Exception {
        String token = registerAndGetToken("alice@test.com", "Tutor Alice", "TUTOR");

        mockMvc.perform(post("/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Calculus",
                                  "category": "Math"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/tutors/profile/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skills": ["calculus", "Linear Algebra"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        long count = skillRepository.count();
        assertThat(count).isEqualTo(2L);
    }
}
