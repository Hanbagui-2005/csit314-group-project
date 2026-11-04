package com.csit314.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Anthony's review on PR #27 - proves PageController actually catches the validation
// error end to end, instead of it escaping to SystemErrorLoggingAdvice as an "unexpected"
// bug and bouncing the customer to a generic error page
@SpringBootTest
@AutoConfigureMockMvc
class SearchBudgetRangeTests {

    @Autowired private MockMvc mvc;

    @Test
    void anInvalidBudgetRangeShowsAFriendlyMessageInsteadOfCrashing() throws Exception {
        mvc.perform(get("/search").param("minBudget", "10000").param("maxBudget", "5000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Minimum budget")))
                .andExpect(content().string(containsString("more than the maximum")));
    }

    @Test
    void aValidBudgetRangeStillRendersNormally() throws Exception {
        mvc.perform(get("/search").param("minBudget", "1000").param("maxBudget", "5000"))
                .andExpect(status().isOk());
    }
}
