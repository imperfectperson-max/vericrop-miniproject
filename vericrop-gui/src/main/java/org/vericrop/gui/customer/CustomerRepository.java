package org.vericrop.gui.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Customer entity.
 * Provides CRUD operations and custom queries for customer management.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    /**
     * Find customer by email address.
     * @param email the email to search for
     * @return Optional containing the customer if found
     */
    Optional<Customer> findByEmail(String email);
    
    /**
     * Find all customers by customer type.
     * @param customerType the type of customer (FARMER, LOGISTICS, RETAILER, CONSUMER)
     * @return list of customers matching the type
     */
    List<Customer> findByCustomerType(String customerType);
    
    /**
     * Find all active customers.
     * @param active the active status
     * @return list of active customers
     */
    List<Customer> findByActive(Boolean active);
    
    /**
     * Check if a customer exists with the given email.
     * @param email the email to check
     * @return true if customer exists, false otherwise
     */
    boolean existsByEmail(String email);
}
