package com.csit314.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// IDR-07 - reproduces the real bug a teammate reported: uploading a file over
// spring.servlet.multipart.max-file-size got misdiagnosed as an expired CSRF token
// ("This form has expired or could not be verified") instead of a clear size error.
//
// This needs a REAL embedded server, not MockMvc - MockMvc never exercises the servlet
// container's own multipart parsing, which is exactly where the original bug lived (see
// UploadSizeLimitFilter's comment for the full mechanism).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:upload_too_large;MODE=MySQL;DB_CLOSE_DELAY=-1")
class UploadSizeLimitIntegrationTests {

    @Autowired
    TestRestTemplate rest;

    @Test
    void anUploadOverTheFileSizeLimitShowsTheRealReasonInsteadOfAFalseCsrfExpiry() {
        // every page carries a CSRF-protected form (the login modal in fragments/layout.html),
        // so any page gives us a real token tied to a real session - exactly like a browser
        ResponseEntity<String> page = rest.getForEntity("/search", String.class);
        String sessionCookie = page.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a JSESSIONID cookie from /search"))
                .split(";")[0];
        Matcher tokenMatch = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(page.getBody());
        assertThat(tokenMatch.find()).as("expected a CSRF token on the rendered page").isTrue();
        String csrfToken = tokenMatch.group(1);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("_csrf", csrfToken);
        body.add("title", "Oversized upload test");
        body.add("photos", new ByteArrayResource(new byte[11 * 1024 * 1024]) {
            @Override
            public String getFilename() {
                return "huge.jpg";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, sessionCookie);

        ResponseEntity<String> response = rest.exchange("/designer/projects", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        // TestRestTemplate follows the redirect automatically, so the body is whatever
        // page it landed on - check it shows the real reason, not the CSRF one
        assertThat(response.getBody())
                .as("should show the real upload-too-large reason, not a false CSRF-expired message")
                .doesNotContain("form has expired or could not be verified")
                .contains("too large");
    }
}
