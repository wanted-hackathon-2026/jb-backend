package com.jachwibangjeongsig.jb.global.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SwaggerGoogleLoginTransformer extends SwaggerIndexPageTransformer {

	private final String clientId;
	private final String script;

	public SwaggerGoogleLoginTransformer(
		SwaggerUiConfigProperties uiProperties,
		SwaggerUiOAuthProperties oauthProperties,
		SwaggerWelcomeCommon welcome,
		ObjectMapperProvider mapperProvider,
		String clientId
	) {
		super(uiProperties, oauthProperties, welcome, mapperProvider);
		if (clientId == null || clientId.isBlank()) {
			throw new IllegalArgumentException("Google Client ID is required for Swagger Google login");
		}
		this.clientId = clientId;
		try {
			this.script = new ClassPathResource("swagger/google-login.js")
				.getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot load Swagger Google login script", exception);
		}
	}

	@Override
	public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
		throws IOException {
		Resource transformed = super.transform(request, resource, chain);
		if (!"index.html".equals(resource.getFilename())) {
			return transformed;
		}
		String html = transformed.getContentAsString(StandardCharsets.UTF_8);
		String injection = "<div id=\"swagger-google-login\" data-client-id=\""
			+ HtmlUtils.htmlEscape(clientId) + "\" data-context-path=\""
			+ HtmlUtils.htmlEscape(request.getContextPath()) + "\"></div>\n<script>\n"
			+ script + "\n</script>\n";
		return new TransformedResource(transformed,
			html.replace("</body>", injection + "</body>").getBytes(StandardCharsets.UTF_8));
	}
}
