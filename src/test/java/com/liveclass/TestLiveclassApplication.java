package com.liveclass;

import org.springframework.boot.SpringApplication;

public class TestLiveclassApplication {

	public static void main(String[] args) {
		SpringApplication.from(LiveclassApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
