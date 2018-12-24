package com.neu.RedisConfig;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Configuration
public class RedisConfiguration {

	
	
	 JedisPool getJedisPool() {
		JedisPool jedisPool = new JedisPool("localhost", 6379);
		return jedisPool;
	}
	
	
	
	@Bean(name = "jedis")
	public Jedis getJedis() {
		Jedis jedis = getJedisPool().getResource();
		return jedis;
	}
}
