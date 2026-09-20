package com.jachwibangjeongsig.jb.global.config;

import com.jachwibangjeongsig.jb.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.ResourceTransformerChain;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SwaggerGoogleLoginTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(SwaggerGoogleLoginConfig.class)
		.withInitializer(context -> {
			SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();
			properties.setPersistAuthorization(true);
			context.getBeanFactory().registerSingleton("uiProperties", properties);
			context.getBeanFactory().registerSingleton("oauthProperties", new SwaggerUiOAuthProperties());
			context.getBeanFactory().registerSingleton("welcome", mock(SwaggerWelcomeCommon.class));
			context.getBeanFactory().registerSingleton("mapper", mock(ObjectMapperProvider.class));
			context.getBeanFactory().registerSingleton("authProperties", new AuthProperties(
				"test-client-id", "access-secret", "refresh-secret", false, List.of()
			));
		});

	@Test
	void disabledByDefault() {
		contextRunner.run(context -> assertThat(context).doesNotHaveBean(SwaggerIndexTransformer.class));
	}

	@Test
	void explicitlyDisabled() {
		contextRunner.withPropertyValues("swagger.google-login.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(SwaggerIndexTransformer.class));
	}

	@Test
	void swaggerDisabledDoesNotRegisterLoginTransformer() {
		contextRunner.withPropertyValues("swagger.google-login.enabled=true", "springdoc.swagger-ui.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(SwaggerIndexTransformer.class));
	}

	@Test
	void enabledRegistersTransformerAndDisablesTokenPersistence() {
		contextRunner.withPropertyValues("swagger.google-login.enabled=true").run(context -> {
			assertThat(context).hasSingleBean(SwaggerIndexTransformer.class);
			assertThat(context.getBean(SwaggerUiConfigProperties.class).getPersistAuthorization()).isFalse();
		});
	}

	@Test
	void injectsOnlyIndexAndEscapesConfiguration() throws Exception {
		SwaggerGoogleLoginTransformer transformer = new SwaggerGoogleLoginTransformer(
			new SwaggerUiConfigProperties(), new SwaggerUiOAuthProperties(),
			mock(SwaggerWelcomeCommon.class), mock(ObjectMapperProvider.class), "client\"<&"
		);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setContextPath("/dev");
		Resource index = resource("index.html", "<html><body><div id=\"swagger-ui\"></div></body></html>");
		String html = transformer.transform(request, index, mock(ResourceTransformerChain.class))
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(html)
			.contains("data-client-id=\"client&quot;&lt;&amp;\"", "data-context-path=\"/dev\"")
			.contains("https://accounts.google.com/gsi/client", "ui.preauthorizeApiKey", "</body></html>")
			.doesNotContain("access-secret", "refresh-secret");
		Resource css = resource("swagger-ui.css", "original-css");
		assertThat(transformer.transform(request, css, mock(ResourceTransformerChain.class))).isSameAs(css);
	}

	private Resource resource(String filename, String contents) {
		return new ByteArrayResource(contents.getBytes(StandardCharsets.UTF_8)) {
			@Override
			public String getFilename() {
				return filename;
			}

			@Override
			public URL getURL() throws MalformedURLException {
				return URI.create("file:/swagger-ui/" + filename).toURL();
			}

			@Override
			public long lastModified() {
				return 0;
			}
		};
	}
}
