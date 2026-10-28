package com.example.frontend;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

    @Test
    void userDetailRedirectsWhenUserServiceIsDown() throws Exception {
        mvc.perform(get("/users/1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andExpect(flash().attribute("error", "user-service indisponible"));
    }

    @Test
    void deleteUserRedirectsWhenUserServiceIsDown() throws Exception {
        mvc.perform(post("/users/1/delete"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andExpect(flash().attribute("error", "user-service indisponible"));
    }
}
