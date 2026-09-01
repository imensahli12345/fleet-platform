package com.fleet.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
//it tells springboot this is a spring boot app
@EnableConfigServer
// hedhy annotation yefhem beha eli hedha howa config server
//ce fichier démarre le service config
//lanche port 8888

public class ConfigServerApplication {
  public static void main(String[] args) {
    SpringApplication.run(ConfigServerApplication.class, args);
  }
}

