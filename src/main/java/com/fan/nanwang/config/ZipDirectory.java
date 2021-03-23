package com.fan.nanwang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "zip-directory")
public class ZipDirectory {

    private String weatherDataPath;

    private String gpzlPath;

    private String zipPath;

    private String dirPath;

    private String forecastDirPath;

    private List<String> list;
}
