package io.github.claimflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claimflow.domain.Claim;
import io.github.claimflow.domain.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

@SpringBootTest(properties = "claimflow.clock.fixed-date=2026-11-06")
@AutoConfigureMockMvc
class ClaimApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ClaimRepository claimRepository;

    private static String fnol(String policy, String type, String lossDate, String email, String coverage,
                               String amount, String documents) {
        return """
                {"policyNumber":"%s","type":"%s","lossDate":"%s","description":"Synthetic test loss",
                 "claimant":{"firstName":"Test","lastName":"Person","email":"%s","phone":"555-0100"},
                 "lineItems":[{"coverage":"%s","description":"repair","amount":%s}],
                 "documents":%s}
                """.formatted(policy, type, lossDate, email, coverage, amount, documents);
    }

    private static final String GOOD_DOCS = """
            [{"type":"POLICE_REPORT","fileName":"report.pdf","contentType":"application/pdf","sizeBytes":1200},
             {"type":"PHOTO","fileName":"a.jpg","contentType":"image/jpeg","sizeBytes":5000},
             {"type":"REPAIR_ESTIMATE","fileName":"est.pdf","contentType":"application/pdf","sizeBytes":800}]""";

    private JsonNode submit(String body) throws Exception {
        String json = mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private JsonNode act(String claim, String action, long version, String extra, ResultMatcher expected)
            throws Exception {
        String body = "{\"action\":\"%s\",\"expectedVersion\":%d,\"actor\":\"adj.test\"%s}"
                .formatted(action, version, extra);
        String json = mvc.perform(post("/api/claims/" + claim + "/actions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(expected).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    @Test
    void lowRiskClaimIsFastTrackedThenPaidAndClosedWithFullAuditTrail() throws Exception {
        JsonNode c = submit(fnol("POL-AUTO-0001", "AUTO", "2026-11-04", "fast@example.test", "COLLISION",
                "2350.75", GOOD_DOCS));
        assertThat(c.get("status").asText()).isEqualTo("FAST_TRACK_APPROVED");
        assertThat(c.get("approvedAmount").decimalValue()).isEqualByComparingTo("1850.75");
        assertThat(c.at("/triage/risk/band").asText()).isEqualTo("LOW");
        assertThat(c.at("/triage/risk/topFactors")).hasSize(3);
        String num = c.get("claimNumber").asText();

        JsonNode paid = act(num, "PAY", c.get("version").asLong(), "", status().isOk());
        assertThat(paid.get("paidAmount").decimalValue()).isEqualByComparingTo("1850.75");
        JsonNode closed = act(num, "CLOSE", paid.get("version").asLong(), "", status().isOk());
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.get("availableActions")).isEmpty();

        mvc.perform(get("/api/claims/" + num + "/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].action").value("SUBMIT"))
                .andExpect(jsonPath("$[2].toStatus").value("FAST_TRACK_APPROVED"))
                .andExpect(jsonPath("$[4].actor").value("adj.test"));
    }

    @Test
    void ruleFailureRoutesToReviewWhereAdjusterApprovesWithinPayable() throws Exception {
        JsonNode c = submit(fnol("POL-AUTO-0004", "AUTO", "2026-11-01", "review@example.test", "COLLISION",
                "3000", "[]"));
        assertThat(c.get("status").asText()).isEqualTo("IN_REVIEW");
        assertThat(c.at("/triage/fastTrack").asBoolean()).isFalse();
        assertThat(c.at("/triage/routingReasons/0").asText()).contains("COVERAGE");
        String num = c.get("claimNumber").asText();
        long v = c.get("version").asLong();

        act(num, "APPROVE", v, ",\"amount\":999999", status().isUnprocessableEntity());
        JsonNode approved = act(num, "APPROVE", v, ",\"amount\":2000,\"reason\":\"liability accepted\"",
                status().isOk());
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void staleVersionIsRejectedWithConflict() throws Exception {
        JsonNode c = submit(fnol("POL-PROP-0003", "PROPERTY", "2026-08-01", "late@example.test", "DWELLING",
                "4000", "[]"));
        assertThat(c.get("status").asText()).isEqualTo("IN_REVIEW");
        String num = c.get("claimNumber").asText();
        long v = c.get("version").asLong();
        act(num, "REQUEST_INFO", v, ",\"reason\":\"send invoice\"", status().isOk());
        act(num, "DENY", v, ",\"reason\":\"late\"", status().isConflict());
    }

    @Test
    void concurrentWritesOfTheSameVersionAreDetectedByJpa() throws Exception {
        JsonNode c = submit(fnol("POL-AUTO-0003", "AUTO", "2026-11-03", "lock@example.test", "AUTO_LIABILITY",
                "52000", "[]"));
        Claim first = claimRepository.findByClaimNumber(c.get("claimNumber").asText()).orElseThrow();
        Claim second = claimRepository.findByClaimNumber(c.get("claimNumber").asText()).orElseThrow();
        first.recordReason("adjuster A");
        claimRepository.saveAndFlush(first);
        second.recordReason("adjuster B");
        assertThatThrownBy(() -> claimRepository.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void duplicateSubmissionIsFlagged() throws Exception {
        String body = fnol("POL-PROP-0002", "PROPERTY", "2026-10-20", "dup@example.test", "CONTENTS", "6100",
                GOOD_DOCS);
        submit(body);
        JsonNode second = submit(body);
        assertThat(second.get("status").asText()).isEqualTo("IN_REVIEW");
        assertThat(second.get("triage").get("rules").toString()).contains("Possible duplicate");
    }

    @Test
    void denyRequiresReasonAndSystemActionsAreRejected() throws Exception {
        JsonNode c = submit(fnol("POL-AUTO-0005", "AUTO", "2026-10-30", "cancelled@example.test", "COLLISION",
                "1800", "[]"));
        String num = c.get("claimNumber").asText();
        long v = c.get("version").asLong();
        act(num, "FAST_TRACK", v, "", status().isBadRequest());
        act(num, "DENY", v, "", status().isUnprocessableEntity());
        JsonNode denied = act(num, "DENY", v, ",\"reason\":\"policy cancelled\"", status().isOk());
        assertThat(denied.get("decisionReason").asText()).isEqualTo("policy cancelled");
    }

    @Test
    void invalidFnolReturns400AndUnknownPolicy404() throws Exception {
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(fnol("POL-AUTO-0001", "AUTO", "2099-01-01", "not-an-email", "COLLISION", "-5", "[]")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(fnol("POL-NOPE", "AUTO", "2026-11-01", "x@example.test", "COLLISION", "100", "[]")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/claims/CLM-MISSING")).andExpect(status().isNotFound());
    }

    @Test
    void lookupByPolicyAndNotesSummary() throws Exception {
        JsonNode c = submit(fnol("POL-PROP-0005", "PROPERTY", "2026-11-02", "notes@example.test", "DWELLING",
                "2750.40", GOOD_DOCS));
        String num = c.get("claimNumber").asText();
        mvc.perform(get("/api/claims").param("policyNumber", "POL-PROP-0005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimNumber").value(num));
        mvc.perform(get("/api/claims")).andExpect(status().isBadRequest());

        mvc.perform(post("/api/claims/" + num + "/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"adj.test\",\"body\":\"Roof inspected. Contractor estimate is $2,750.\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/claims/" + num + "/notes/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("offline-extractive"))
                .andExpect(jsonPath("$.keyPoints[1]").value("Contractor estimate is $2,750."));
    }
}
