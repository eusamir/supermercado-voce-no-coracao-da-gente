package com.vocenocoracao.supermercado_api;

import org.springframework.boot.SpringApplication;

public class TestSupermercadoApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(SupermercadoApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
