package com.pablo.jobflow;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JobflowApplication {

	static {
		System.setProperty("user.timezone", "UTC");
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(JobflowApplication.class, args);
	}

}
