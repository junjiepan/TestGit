package com.example.scriptdemo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "conf")
public class ConfProperties {
    private String assetId;
    private String outputFilePath;
}

