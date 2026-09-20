package com.jachwibangjeongsig.jb.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"swagger.google-login.enabled=true",
	"springdoc.swagger-ui.persist-authorization=true",
	"auth.google-client-id=test-google-client-id",
	"auth.access-token-secret=test-access-token-secret-with-at-least-32-bytes",
	"auth.refresh-token-secret=test-refresh-token-secret-with-at-least-32-bytes",
	"vworld.api-key=test-vworld-api-key",
	"auth.cookie-secure=false",
	"auth.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
@Testcontainers
class SwaggerGoogleLoginApiTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Autowired
	MockMvc mockMvc;

	@Test
	void enabledSwaggerServesLoginScriptAndPreservesInitializer() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("data-client-id=\"test-google-client-id\"")))
			.andExpect(content().string(containsString("https://accounts.google.com/gsi/client")))
			.andExpect(content().string(containsString("ui.preauthorizeApiKey")));
		mockMvc.perform(get("/swagger-ui/swagger-initializer.js"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("SwaggerUIBundle")))
			.andExpect(content().string(containsString("/v3/api-docs/swagger-config")));
		mockMvc.perform(get("/v3/api-docs/swagger-config"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.persistAuthorization").value(false));
	}
}
