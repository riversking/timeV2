package com.rivers.nba;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class NBAApplication {

    public static void main(String[] args) {
        SpringApplication.run(NBAApplication.class, args);
    }

}
