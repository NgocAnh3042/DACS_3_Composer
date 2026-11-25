package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.api.ApiEnvelope;
import com.example.backend.api.ApiErrorResponse;
import com.example.backend.api.ApiException;
import com.example.backend.api.GlobalExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApplicationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    @Test
    void contextLoads() { }

    @Test
    void apiHealthChecksTheTestDatabaseAndReturnsExpectedShape() throws Exception {
        mockMvc.perform(get("/api/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.database").value("connected"));
    }

    @Test
    void corsPreflightReturns204WithAllowedApiHeaders() throws Exception {
        mockMvc.perform(options("/api/users")
                        .header("Origin", "https://example.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Headers", "Authorization, Content-Type"));
    }

    @Test
    void commonEnvelopeAndErrorUseThePhpContractShapes() throws Exception {
        assertEquals("{\"data\":{\"id\":\"123\"}}",
                jsonMapper.writeValueAsString(ApiEnvelope.of(Map.of("id", "123"))));

        var errorResponse = new GlobalExceptionHandler().handleApiException(
                new ApiException(HttpStatus.CONFLICT, "Xung đột trạng thái"));
        assertEquals(409, errorResponse.getStatusCode().value());
        assertEquals("{\"error\":\"Xung đột trạng thái\"}",
                jsonMapper.writeValueAsString((ApiErrorResponse) errorResponse.getBody()));
    }
}
