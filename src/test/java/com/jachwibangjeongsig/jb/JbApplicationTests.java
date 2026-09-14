package com.jachwibangjeongsig.jb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
	"auth.google-client-id=test-google-client-id",
	"auth.access-token-secret=test-access-token-secret-with-at-least-32-bytes",
	"auth.refresh-token-secret=test-refresh-token-secret-with-at-least-32-bytes"
})
@Testcontainers
class JbApplicationTests {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Test
	void contextLoads() {
	}

}
