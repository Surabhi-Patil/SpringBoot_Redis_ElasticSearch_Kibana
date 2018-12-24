package com.neu.RedisProject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import com.neu.PlanController.PlanController;

@SpringBootApplication
@EnableAutoConfiguration
@ComponentScan({"com.neu.PlanController", "com.neu.RedisConfig"})
public class RedisProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(RedisProjectApplication.class, args);
	}
}