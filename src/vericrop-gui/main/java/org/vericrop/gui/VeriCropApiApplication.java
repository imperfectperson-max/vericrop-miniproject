package org.vericrop.gui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot Application for VeriCrop REST API.
 * Provides REST endpoints for quality evaluation and shipment management.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.vericrop"})
public class VeriCropApiApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(VeriCropApiApplication.class, args);
    }
}
