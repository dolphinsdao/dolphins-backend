package com.dolphinsdao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@SpringBootApplication
@EnableScheduling
@Slf4j public class DolphinsApplication {
    public static void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException, IOException {
        ConfigurableApplicationContext context = SpringApplication.run(DolphinsApplication.class, args);
    }

    @Bean public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
