package org.vericrop.gui.customer;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Customer management.
 * Provides CRUD endpoints for customer operations.
 */
@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);
    
    private final CustomerService customerService;
    
    @Autowired
    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
        logger.info("CustomerController initialized");
    }
    
    /**
     * Get all customers.
     * GET /api/customers
     */
    @GetMapping
    public ResponseEntity<List<Customer>> getAllCustomers(
            @RequestParam(required = false) String type) {
        logger.debug("GET /api/customers - type: {}", type);
        
        List<Customer> customers;
        if (type != null && !type.isEmpty()) {
            customers = customerService.getCustomersByType(type);
        } else {
            customers = customerService.getAllCustomers();
        }
        
        return ResponseEntity.ok(customers);
    }
    
    /**
     * Get customer by ID.
     * GET /api/customers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        logger.debug("GET /api/customers/{}", id);
        
        return customerService.getCustomerById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Create a new customer.
     * POST /api/customers
     */
    @PostMapping
    public ResponseEntity<Customer> createCustomer(@Valid @RequestBody Customer customer) {
        logger.info("POST /api/customers - Creating customer: {}", customer.getEmail());
        
        try {
            Customer createdCustomer = customerService.createCustomer(customer);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdCustomer);
        } catch (IllegalArgumentException e) {
            logger.error("Error creating customer: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Update an existing customer.
     * PUT /api/customers/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody Customer customer) {
        logger.info("PUT /api/customers/{} - Updating customer", id);
        
        try {
            Customer updatedCustomer = customerService.updateCustomer(id, customer);
            return ResponseEntity.ok(updatedCustomer);
        } catch (ResourceNotFoundException e) {
            logger.error("Customer not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            logger.error("Error updating customer: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Delete a customer.
     * DELETE /api/customers/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        logger.info("DELETE /api/customers/{}", id);
        
        try {
            customerService.deleteCustomer(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            logger.error("Customer not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Exception handler for validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.badRequest().body(errors);
    }
    
    /**
     * Exception handler for illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }
}
