package br.com.conectapet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ConectaPetApplication {

    public static void main(String[] args) {
        // Persistencia em UTC; a conversao para America/Sao_Paulo acontece na borda.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ConectaPetApplication.class, args);
    }
}
