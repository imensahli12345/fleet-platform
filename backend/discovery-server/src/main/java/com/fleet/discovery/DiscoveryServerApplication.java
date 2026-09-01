package com.fleet.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
//this annotation make the app boot life a spring boot app
@EnableEurekaServer
//tells that this is an eureka registry it turns the app into discovery server

public class DiscoveryServerApplication {
  public static void main(String[] args) {

      SpringApplication.run(DiscoveryServerApplication.class, args);
  }
}

