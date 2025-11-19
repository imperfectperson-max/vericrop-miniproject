package org.vericrop.gui.customer;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Customer management operations.
 */
public interface CustomerService {
    
    /**
     * Get all customers.
     * @return list of all customers
     */
    List<Customer> getAllCustomers();
    
    /**
     * Get customer by ID.
     * @param id the customer ID
     * @return Optional containing the customer if found
     */
    Optional<Customer> getCustomerById(Long id);
    
    /**
     * Create a new customer.
     * @param customer the customer to create
     * @return the created customer
     */
    Customer createCustomer(Customer customer);
    
    /**
     * Update an existing customer.
     * @param id the customer ID
     * @param customer the customer data to update
     * @return the updated customer
     */
    Customer updateCustomer(Long id, Customer customer);
    
    /**
     * Delete a customer by ID.
     * @param id the customer ID
     */
    void deleteCustomer(Long id);
    
    /**
     * Find customers by type.
     * @param customerType the customer type
     * @return list of customers matching the type
     */
    List<Customer> getCustomersByType(String customerType);
    
    /**
     * Check if email already exists.
     * @param email the email to check
     * @return true if email exists, false otherwise
     */
    boolean emailExists(String email);
}
