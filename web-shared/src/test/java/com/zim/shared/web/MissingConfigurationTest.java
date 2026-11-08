package com.zim.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A slice that forgets {@link WebSharedConfiguration} fails loudly: the
 * mapping is not silently replaced, the framework default answers (no
 * {@code code}), so every error assertion of that slice fails.
 */
@WebMvcTest(MissingConfigurationTest.ProbeController.class)
@Import(MissingConfigurationTest.ProbeController.class)
class MissingConfigurationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldNotMapExceptionsWithoutTheConfiguration() throws Exception {
    mockMvc.perform(get("/api/test/missing/{id}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @RestController
  static class ProbeController {

    @GetMapping("/api/test/missing/{id}")
    String byId(@org.springframework.web.bind.annotation.PathVariable
                java.util.UUID id) {
      return id.toString();
    }
  }
}
