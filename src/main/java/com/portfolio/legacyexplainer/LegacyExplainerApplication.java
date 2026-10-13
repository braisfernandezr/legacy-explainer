package com.portfolio.legacyexplainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LegacyExplainerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LegacyExplainerApplication.class, args);
	}

}
