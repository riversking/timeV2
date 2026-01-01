package com.rivers.user;

import com.google.protobuf.util.JsonFormat;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.protobuf.ProtobufJsonDecoder;
import org.springframework.http.codec.protobuf.ProtobufJsonEncoder;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan(basePackages = "com.rivers.user.mapper")
public class UserServerApplication {

    static void main(String[] args) {
        SpringApplication.run(UserServerApplication.class, args);
    }

}
