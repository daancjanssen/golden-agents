package org.uu.nl.goldenagents;

import ch.rasc.sse.eventbus.SseEventBus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.uu.nl.goldenagents.util.StartupArgumentsParser;
import org.uu.nl.net2apl.core.platform.Platform;

import java.util.logging.Level;
import java.util.logging.Logger;

@Configuration
public class AppConfig {

	@Value("${ALLOWED_ORIGINS:http://localhost:4200}")
	private String allowedOrigins;

	@Value("${management.endpoints.web.cors.allowed-origins}")
	private String appOriginsAllowed;

	@Bean
	public WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			
			@Override
			public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
				configurer.setDefaultTimeout(-1);
			}
			
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				String[] allowedOriginsList = AppConfig.this.allowedOrigins.split(",");

				for(String allowedOrigin : allowedOriginsList) {
					Logger.getLogger(AppConfig.class.getName()).log(
							Level.INFO, "Adding allowed origins to CORS: " + allowedOrigin
					);
				}
				Logger.getLogger(AppConfig.class.getName()).log(
						Level.INFO, "application.yml CORS origins: " + AppConfig.this.appOriginsAllowed
				);

				registry
					.addMapping("/api/agent/**")
					.allowedOrigins(allowedOriginsList)
					.allowedHeaders("*")
					.allowedMethods("GET", "POST", "DELETE");
				registry
					.addMapping("/api/message/**")
					.allowedOrigins(allowedOriginsList)
					.allowedHeaders("*")
					.allowedMethods("GET", "POST", "DELETE");
				registry
					.addMapping("/api/sse/**")
					.allowedOrigins(allowedOriginsList)
					.allowedHeaders("*")
					.allowedMethods("GET");
				registry
					.addMapping("/api/agent/db/**")
					.allowedOrigins(allowedOriginsList)
					.allowedHeaders("*")
					.allowedMethods("GET");
				registry
					.addMapping("/api/agent/embedding/**")
					.allowedOrigins(allowedOriginsList)
					.allowedHeaders("*")
					.allowedMethods("GET");
				registry
					.addMapping("/api/agent/user/**")
					.allowedOrigins(allowedOriginsList)
					.allowedHeaders("*")
					.allowedMethods("GET", "POST");
				registry
					.addMapping("sparql/**")
					.allowedOrigins("*")
					.allowedHeaders("*")
					.allowedMethods("GET", "POST");
			}
		};
	}

	@Bean("platform")
	@Autowired
	public Platform platform(SseEventBus serverEventBus) {
		Platform p = new StartupArgumentsParser(serverEventBus).getPlatform();
		p.setLogger(new GoldenLogger());
		return p;
	}
}
