package com.interview.homeshoptraffic;

import com.interview.homeshoptraffic.traffic.TrafficProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TrafficProperties.class)
public class HomeShopTrafficApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomeShopTrafficApplication.class, args);
    }
}
