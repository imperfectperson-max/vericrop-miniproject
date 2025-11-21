package org.vericrop.gui.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for VeriCrop REST API documentation.
 * 
 * Access Swagger UI at: http://localhost:8080/swagger-ui.html
 * Access OpenAPI JSON at: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${vericrop.api.version:1.0.0}")
    private String apiVersion;

    @Value("${vericrop.api.server.prod.url:https://api.vericrop.example.com}")
    private String prodServerUrl;

    @Value("${vericrop.api.server.staging.url:https://staging-api.vericrop.example.com}")
    private String stagingServerUrl;

    @Value("${vericrop.api.contact.name:VeriCrop Team}")
    private String contactName;

    @Value("${vericrop.api.contact.email:support@vericrop.example.com}")
    private String contactEmail;

    @Value("${vericrop.api.contact.url:https://github.com/imperfectperson-max/vericrop-miniproject}")
    private String contactUrl;

    @Bean
    public OpenAPI vericropOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VeriCrop Supply Chain Management API")
                        .description("""
                                REST API for VeriCrop agricultural supply chain management system.
                                
                                ## Features
                                - **Quality Evaluation**: AI-powered fruit quality assessment
                                - **Blockchain Ledger**: Immutable supply chain tracking
                                - **Kafka Messaging**: Real-time event streaming
                                - **Analytics**: Supply chain insights and reporting
                                
                                ## Authentication
                                This API uses JWT tokens for authentication. Include the token in the Authorization header:
                                ```
                                Authorization: Bearer <your-token>
                                ```
                                
                                ## Rate Limiting
                                - 100 requests per minute per IP
                                - 1000 requests per hour per user
                                
                                ## Error Handling
                                All errors follow RFC 7807 Problem Details format.
                                """)
                        .version(apiVersion)
                        .contact(new Contact()
                                .name(contactName)
                                .url(contactUrl)
                                .email(contactEmail))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url(prodServerUrl)
                                .description("Production Server"),
                        new Server()
                                .url(stagingServerUrl)
                                .description("Staging Server")))
                .tags(List.of(
                        new Tag()
                                .name("Evaluation")
                                .description("Quality evaluation and assessment endpoints"),
                        new Tag()
                                .name("Quality")
                                .description("Quality management and monitoring"),
                        new Tag()
                                .name("Delivery")
                                .description("Shipment and delivery tracking"),
                        new Tag()
                                .name("Messaging")
                                .description("User messaging and notifications"),
                        new Tag()
                                .name("Health")
                                .description("Service health and monitoring endpoints"),
                        new Tag()
                                .name("Ledger")
                                .description("Blockchain ledger operations")));
    }
}
