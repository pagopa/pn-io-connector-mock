package it.pagopa.pn.ioconnectormock.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.MessageContent;
import it.pagopa.pn.ioconnectormock.generated.openapi.server.v1.dto.NewMessage;
import it.pagopa.pn.ioconnectormock.localstack.LocalStackTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(LocalStackTestConfig.class)
class IOMockControllerIntegrationTest {

    private static final String SEQUENCE_NAME = "OK_READ_THEN_PAID";
    private static final String FISCAL_CODE = "RSSMRA80A01H501U";
    private static final String PROFILES = "/io-connector-mock/profiles";
    private static final String MESSAGES = "/io-connector-mock/messages";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void profileAllowedByDefault() throws Exception {
        mockMvc.perform(post(PROFILES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new FiscalCodePayload(FISCAL_CODE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender_allowed").value(true));
    }

    @Test
    void submitWithoutMarkerReturns400() throws Exception {
        mockMvc.perform(post(MESSAGES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(newMessage("no marker here"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitWithInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post(MESSAGES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fiscal_code\":\"" + FISCAL_CODE + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void headerNotRequired() throws Exception {
        MvcResult result = mockMvc.perform(post(MESSAGES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(newMessage("@io:" + SEQUENCE_NAME))))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("MOCK-OK_READ_THEN_PAID");
    }

    @Test
    void submitUnknownSequenceReturns400() throws Exception {
        mockMvc.perform(post(MESSAGES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(newMessage("@io:UNKNOWN_SEQUENCE"))))
                .andExpect(status().isBadRequest());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private NewMessage newMessage(String subject) {
        return new NewMessage(FISCAL_CODE, new MessageContent(subject, "body"))
                .featureLevelType(NewMessage.FeatureLevelTypeEnum.ADVANCED);
    }
}
