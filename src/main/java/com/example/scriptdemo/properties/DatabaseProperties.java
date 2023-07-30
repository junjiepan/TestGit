package com.example.scriptdemo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "database")
public class DatabaseProperties {

    private String url;
    private String username;
    private String password;
}

