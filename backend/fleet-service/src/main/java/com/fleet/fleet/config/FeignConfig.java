package com.fleet.fleet.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

// Kept off FleetServiceApplication so @WebMvcTest slices do not try to build Feign clients.
@Configuration
@EnableFeignClients(basePackages = "com.fleet.fleet.client")
public class FeignConfig {
}
