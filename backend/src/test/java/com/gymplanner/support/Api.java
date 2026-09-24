package com.gymplanner.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.gymplanner.shared.security.AuthenticatedUser;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tiny JSON-over-MockMvc client used by integration tests. */
@TestComponent
public class Api {

    private final MockMvc mvc;
    private final TestFixtures fixtures;

    public Api(MockMvc mvc, TestFixtures fixtures) {
        this.mvc = mvc;
        this.fixtures = fixtures;
    }

    public record Response(int status, String body) {

        public <T> T read(String path) {
            return JsonPath.read(body, path);
        }

        public Response expect(int expected) {
            Assertions.assertThat(status).as("HTTP status, body: %s", body).isEqualTo(expected);
            return this;
        }

        public Response expectCode(int expectedStatus, String code) {
            expect(expectedStatus);
            Assertions.assertThat((String) read("$.code")).isEqualTo(code);
            return this;
        }
    }

    public Response get(AuthenticatedUser user, String url) {
        return perform(user, MockMvcRequestBuilders.get(url));
    }

    public Response post(AuthenticatedUser user, String url, String json) {
        return perform(user, withBody(MockMvcRequestBuilders.post(url), json));
    }

    public Response put(AuthenticatedUser user, String url, String json) {
        return perform(user, withBody(MockMvcRequestBuilders.put(url), json));
    }

    public Response delete(AuthenticatedUser user, String url) {
        return perform(user, MockMvcRequestBuilders.delete(url).with(csrf()));
    }

    private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, String json) {
        builder.with(csrf());
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return builder;
    }

    private Response perform(AuthenticatedUser user, MockHttpServletRequestBuilder builder) {
        try {
            if (user != null) {
                builder.with(fixtures.as(user));
            }
            MvcResult result = mvc.perform(builder).andReturn();
            return new Response(result.getResponse().getStatus(),
                    result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
