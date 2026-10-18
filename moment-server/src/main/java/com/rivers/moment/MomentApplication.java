package com.rivers.moment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MomentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MomentApplication.class, args);
    }

}
