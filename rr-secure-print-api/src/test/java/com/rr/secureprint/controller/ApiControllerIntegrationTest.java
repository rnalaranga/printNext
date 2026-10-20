package com.rr.secureprint.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rr.secureprint.dto.LoginRequest;
import com.rr.secureprint.dto.ReleaseRequest;
import com.rr.secureprint.dto.SimulationJobRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.printer.simulation-mode=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthEndpointShouldReturnUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("nexPrint Server"))
                .andExpect(jsonPath("$.toshibaCompatibility.ewbSupported").value(true));
    }

    @Test
    void fullPullPrintLifecycleFlow() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Submit a simulated job for John Doe
        SimulationJobRequest simRequest = new SimulationJobRequest("rr", "Site-Foundation-Plans.pdf", 4, false, true, 1, "ENG-WS01");
        MvcResult submitResult = mockMvc.perform(post("/api/simulation/submit-job")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("rr"))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andReturn();

        String jobIdStr = objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("id").asText();
        UUID jobId = UUID.fromString(jobIdStr);

        // 2. Unauthenticated access to /api/jobs must return 401
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isUnauthorized());

        // 3. Authenticate at Toshiba Touchscreen as John Doe (PIN / Password: 1234)
        LoginRequest loginRequest = new LoginRequest("rr", "1234", "1234", null);
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("rr"))
                .andExpect(jsonPath("$.userSid").isNotEmpty());

        // 4. Retrieve pending jobs for John Doe
        mockMvc.perform(get("/api/jobs").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // 5. Release the job to Toshiba queue
        ReleaseRequest releaseRequest = new ReleaseRequest("TOSHIBA-3518A-HQ");
        mockMvc.perform(post("/api/jobs/" + jobId + "/release")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.message", containsString("SIMULATED RELEASE SUCCESSFUL")));

        // 6. Attempting to release the SAME job a second time must fail (concurrency/state guard)
        mockMvc.perform(post("/api/jobs/" + jobId + "/release")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseRequest)))
                .andExpect(status().isConflict());

        // 7. Logout
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());
    }
}

