package com.xqt.saas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.xqt.saas", "com.finance.saas"})
public class XqtBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(XqtBackendApplication.class, args);
    }

}
