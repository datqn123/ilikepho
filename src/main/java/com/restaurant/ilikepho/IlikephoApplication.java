package com.restaurant.ilikepho;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class IlikephoApplication {

	public static void main(String[] args) {
		SpringApplication.run(IlikephoApplication.class, args);
	}

}
