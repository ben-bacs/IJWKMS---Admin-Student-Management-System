package edu.wvsu.ijwkms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
        })
class IjwkmsNextBackendApplicationTests {

    @MockitoBean
    JdbcClient jdbcClient;

    @Autowired
    CookieCsrfTokenRepository csrfTokenRepository;

    @Test
    void contextLoads() {}

    @Test
    void configuresBrowserCsrfCookieAndHeaderContract() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        var token = csrfTokenRepository.generateToken(request);

        csrfTokenRepository.saveToken(token, request, response);

        assertThat(token.getHeaderName()).isEqualTo("X-XSRF-TOKEN");
        assertThat(response.getCookie("XSRF-TOKEN")).isNotNull().satisfies(cookie -> assertThat(cookie.isHttpOnly())
                .isFalse());
    }
}
