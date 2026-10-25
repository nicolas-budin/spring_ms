package com.example.frontend;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sans backends démarrés, la page d'accueil doit tout de même s'afficher avec un message d'erreur.
 */
@SpringBootTest(properties = { "services.user.url=http://localhost:1", "services.notification.url=http://localhost:1" })
@AutoConfigureMockMvc
class FrontendApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void indexRendersWhenBackendsAreDown() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("indisponible")))
            .andExpect(content().string(containsString("swagger-ui.html")));
    }
}
