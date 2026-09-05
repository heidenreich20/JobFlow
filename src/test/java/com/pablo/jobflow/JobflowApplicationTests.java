package com.pablo.jobflow;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JobflowApplicationTests {

	static {
		System.setProperty("user.timezone", "UTC");
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@Test
	void contextLoads() {
	}

}
