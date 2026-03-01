package com.rivers.batch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;


@SpringBootApplication
@EnableDiscoveryClient
@MapperScan(basePackages = "com.rivers.batch.mapper")
@EnableFeignClients
public class TimerBatchApplication {

    static void main(String[] args) {
        SpringApplication.run(TimerBatchApplication.class, args);
    }
}
