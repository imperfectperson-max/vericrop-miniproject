package org.vericrop.gui.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of CustomerService interface.
 * Provides business logic for customer management operations.
 */
@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomerServiceImpl.class);
    
    private final CustomerRepository customerRepository;
    
    @Autowired
    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        logger.debug("Fetching all customers");
        return customerRepository.findAll();
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerById(Long id) {
        logger.debug("Fetching customer with id: {}", id);
        return customerRepository.findById(id);
    }
    
    @Override
    public Customer createCustomer(Customer customer) {
        logger.info("Creating new customer: {}", customer.getEmail());
        
        // Check if email already exists
        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new IllegalArgumentException("Customer with email " + customer.getEmail() + " already exists");
        }
        
        Customer savedCustomer = customerRepository.save(customer);
        logger.info("Customer created successfully with id: {}", savedCustomer.getId());
        return savedCustomer;
    }
    
    @Override
    public Customer updateCustomer(Long id, Customer customer) {
        logger.info("Updating customer with id: {}", id);
        
        Customer existingCustomer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        
        // Check if email is being changed and if new email already exists
        if (!existingCustomer.getEmail().equals(customer.getEmail()) 
                && customerRepository.existsByEmail(customer.getEmail())) {
            throw new IllegalArgumentException("Customer with email " + customer.getEmail() + " already exists");
        }
        
        // Update fields
        existingCustomer.setName(customer.getName());
        existingCustomer.setEmail(customer.getEmail());
        existingCustomer.setPhone(customer.getPhone());
        existingCustomer.setCustomerType(customer.getCustomerType());
        existingCustomer.setAddress(customer.getAddress());
        existingCustomer.setActive(customer.getActive());
        
        Customer updatedCustomer = customerRepository.save(existingCustomer);
        logger.info("Customer updated successfully: {}", updatedCustomer.getId());
        return updatedCustomer;
    }
    
    @Override
    public void deleteCustomer(Long id) {
        logger.info("Deleting customer with id: {}", id);
        
        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Customer not found with id: " + id);
        }
        
        customerRepository.deleteById(id);
        logger.info("Customer deleted successfully: {}", id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Customer> getCustomersByType(String customerType) {
        logger.debug("Fetching customers by type: {}", customerType);
        return customerRepository.findByCustomerType(customerType);
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return customerRepository.existsByEmail(email);
    }
}
