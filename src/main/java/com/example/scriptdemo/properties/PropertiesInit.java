package com.example.scriptdemo.properties;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Data
public class PropertiesInit {

    private String assetId;
    private String outputFilePath;
    private List<String> assetQueries;
    private List<String> entityQueries;
    private String dbUrl;
    private String dbUser;
    private String dbPassword;

    public PropertiesInit(
            ConfProperties confProperties,
            AssetQueryProperties assetQueryProperties,
            EntityQueryProperties entityQueryPropertiesProperties,
            DatabaseProperties databaseProperties) {
        this.assetId = confProperties.getAssetId();
        this.outputFilePath = confProperties.getOutputFilePath();
        this.assetQueries = assetQueryProperties.getQueries();
        this.entityQueries = entityQueryPropertiesProperties.getQueries();
        this.dbUrl = databaseProperties.getUrl();
        this.dbUser = databaseProperties.getUsername();
        this.dbPassword = databaseProperties.getPassword();
    }

}
