package com.jachwibangjeongsig.jb.global.config;

import com.jachwibangjeongsig.jb.auth.config.AuthProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerGoogleLoginConfig {

	@Bean
	@ConditionalOnProperty(prefix = "swagger.google-login", name = "enabled", havingValue = "true")
	SwaggerIndexTransformer swaggerGoogleLoginTransformer(
		SwaggerUiConfigProperties uiProperties,
		SwaggerUiOAuthProperties oauthProperties,
		SwaggerWelcomeCommon welcome,
		ObjectMapperProvider mapperProvider,
		AuthProperties authProperties
	) {
		uiProperties.setPersistAuthorization(false);
		return new SwaggerGoogleLoginTransformer(
			uiProperties, oauthProperties, welcome, mapperProvider, authProperties.googleClientId()
		);
	}
}
