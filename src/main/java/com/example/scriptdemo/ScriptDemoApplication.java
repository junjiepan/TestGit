package com.example.scriptdemo;

import com.example.scriptdemo.properties.ConfProperties;
import com.example.scriptdemo.properties.DatabaseProperties;
import com.example.scriptdemo.properties.PropertiesInit;
import com.example.scriptdemo.properties.AssetQueryProperties;
import com.example.scriptdemo.script.assetDemo;
import com.example.scriptdemo.script.entityDemo;
import com.example.scriptdemo.script.test01;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.sql.SQLException;

@SpringBootApplication
@Configuration
@EnableConfigurationProperties({DatabaseProperties.class, AssetQueryProperties.class, ConfProperties.class})
public class ScriptDemoApplication {

    public static void main(String[] args) throws SQLException, IOException {
        ConfigurableApplicationContext context = SpringApplication.run(ScriptDemoApplication.class, args);
        PropertiesInit propertiesInit = context.getBean(PropertiesInit.class);
        assetDemo assetBean = new assetDemo(propertiesInit);
        entityDemo entityBean = new entityDemo(propertiesInit);
        assetBean.generateInsertScript();
        entityBean.generateInsertScript();

        System.out.println("\n资产固化文件及该资产下全部实体固化文件生成成功!");
    }

}
