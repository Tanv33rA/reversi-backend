package org.abubusoft.reversi.server.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.abubusoft.reversi.server.model.User;
import org.abubusoft.reversi.server.model.UserRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertNotNull;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HttpRequestTest {
  private static Logger logger = LoggerFactory.getLogger(HttpRequestTest.class);
  private String baseUrl;

  @LocalServerPort
  private int port;

  private ObjectMapper objectMapper;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @BeforeEach
  public void beforeAll() {
    baseUrl = "http://localhost:" + port + "/";
  }

  @Test
  public void greetingShouldReturnDefaultMessage() throws Exception {
    UserRegistration userRegistration = new UserRegistration();
    userRegistration.setName("ciao");
    User user1 = restTemplate.postForEntity(baseUrl + "api/v1/public/users", userRegistration, User.class).getBody();
    User user2 = restTemplate.postForEntity(baseUrl + "api/v1/public/users", userRegistration, User.class).getBody();

    user1 = restTemplate.patchForObject(baseUrl + String.format("api/v1/public/users/%s/ready", user1.getId()), HttpMethod.PUT, User.class);
    user2 = restTemplate.patchForObject(baseUrl + String.format("api/v1/public/users/%s/ready", user2.getId()), HttpMethod.PUT, User.class);

    logger.info(objectMapper.writeValueAsString(user1));
    logger.info(objectMapper.writeValueAsString(user2));
    assertNotNull(user1.getId());
    assertNotNull(user2.getId());
  }
}
