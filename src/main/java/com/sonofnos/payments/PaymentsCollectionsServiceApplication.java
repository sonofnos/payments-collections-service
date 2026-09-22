package com.sonofnos.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentsCollectionsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentsCollectionsServiceApplication.class, args);
	}

}
