package com.leucine.setup;

import com.leucine.setup.datasource.TargetProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TargetProperties.class)
public class SetupApplication {

  public static void main(String[] args) {
    SpringApplication.run(SetupApplication.class, args);
  }
}
