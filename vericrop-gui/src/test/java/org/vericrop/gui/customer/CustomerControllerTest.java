package org.vericrop.gui.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for CustomerController using MockMVC.
 */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private CustomerService customerService;
    
    private Customer testCustomer;
    
    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("John Doe");
        testCustomer.setEmail("john.doe@example.com");
        testCustomer.setPhone("+1234567890");
        testCustomer.setCustomerType("FARMER");
        testCustomer.setAddress("123 Farm Road");
        testCustomer.setActive(true);
        testCustomer.setCreatedAt(LocalDateTime.now());
        testCustomer.setUpdatedAt(LocalDateTime.now());
    }
    
    @Test
    void getAllCustomers_ShouldReturnListOfCustomers() throws Exception {
        // Arrange
        List<Customer> customers = Arrays.asList(testCustomer);
        when(customerService.getAllCustomers()).thenReturn(customers);
        
        // Act & Assert
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[0].email").value("john.doe@example.com"));
        
        verify(customerService, times(1)).getAllCustomers();
    }
    
    @Test
    void getAllCustomers_WithTypeFilter_ShouldReturnFilteredCustomers() throws Exception {
        // Arrange
        List<Customer> customers = Arrays.asList(testCustomer);
        when(customerService.getCustomersByType("FARMER")).thenReturn(customers);
        
        // Act & Assert
        mockMvc.perform(get("/api/customers?type=FARMER"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].customerType").value("FARMER"));
        
        verify(customerService, times(1)).getCustomersByType("FARMER");
    }
    
    @Test
    void getCustomerById_WhenExists_ShouldReturnCustomer() throws Exception {
        // Arrange
        when(customerService.getCustomerById(1L)).thenReturn(Optional.of(testCustomer));
        
        // Act & Assert
        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"));
        
        verify(customerService, times(1)).getCustomerById(1L);
    }
    
    @Test
    void getCustomerById_WhenNotExists_ShouldReturn404() throws Exception {
        // Arrange
        when(customerService.getCustomerById(999L)).thenReturn(Optional.empty());
        
        // Act & Assert
        mockMvc.perform(get("/api/customers/999"))
                .andExpect(status().isNotFound());
        
        verify(customerService, times(1)).getCustomerById(999L);
    }
    
    @Test
    void createCustomer_WithValidData_ShouldReturnCreated() throws Exception {
        // Arrange
        Customer newCustomer = new Customer();
        newCustomer.setName("Jane Smith");
        newCustomer.setEmail("jane.smith@example.com");
        newCustomer.setCustomerType("RETAILER");
        
        when(customerService.createCustomer(any(Customer.class))).thenReturn(testCustomer);
        
        // Act & Assert
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCustomer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
        
        verify(customerService, times(1)).createCustomer(any(Customer.class));
    }
    
    @Test
    void createCustomer_WithInvalidData_ShouldReturn400() throws Exception {
        // Arrange
        Customer invalidCustomer = new Customer();
        invalidCustomer.setEmail("invalid-email"); // Invalid email format
        
        // Act & Assert
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCustomer)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void createCustomer_WithDuplicateEmail_ShouldReturn400() throws Exception {
        // Arrange
        Customer newCustomer = new Customer();
        newCustomer.setName("Jane Smith");
        newCustomer.setEmail("john.doe@example.com"); // Duplicate email
        newCustomer.setCustomerType("RETAILER");
        
        when(customerService.createCustomer(any(Customer.class)))
                .thenThrow(new IllegalArgumentException("Customer with email john.doe@example.com already exists"));
        
        // Act & Assert
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCustomer)))
                .andExpect(status().isBadRequest());
        
        verify(customerService, times(1)).createCustomer(any(Customer.class));
    }
    
    @Test
    void updateCustomer_WhenExists_ShouldReturnUpdatedCustomer() throws Exception {
        // Arrange
        Customer updatedCustomer = new Customer();
        updatedCustomer.setName("John Updated");
        updatedCustomer.setEmail("john.updated@example.com");
        updatedCustomer.setCustomerType("FARMER");
        
        testCustomer.setName("John Updated");
        when(customerService.updateCustomer(eq(1L), any(Customer.class))).thenReturn(testCustomer);
        
        // Act & Assert
        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedCustomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        
        verify(customerService, times(1)).updateCustomer(eq(1L), any(Customer.class));
    }
    
    @Test
    void updateCustomer_WhenNotExists_ShouldReturn404() throws Exception {
        // Arrange
        Customer updatedCustomer = new Customer();
        updatedCustomer.setName("John Updated");
        updatedCustomer.setEmail("john.updated@example.com");
        updatedCustomer.setCustomerType("FARMER");
        
        when(customerService.updateCustomer(eq(999L), any(Customer.class)))
                .thenThrow(new ResourceNotFoundException("Customer not found with id: 999"));
        
        // Act & Assert
        mockMvc.perform(put("/api/customers/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedCustomer)))
                .andExpect(status().isNotFound());
        
        verify(customerService, times(1)).updateCustomer(eq(999L), any(Customer.class));
    }
    
    @Test
    void deleteCustomer_WhenExists_ShouldReturn204() throws Exception {
        // Arrange
        doNothing().when(customerService).deleteCustomer(1L);
        
        // Act & Assert
        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isNoContent());
        
        verify(customerService, times(1)).deleteCustomer(1L);
    }
    
    @Test
    void deleteCustomer_WhenNotExists_ShouldReturn404() throws Exception {
        // Arrange
        doThrow(new ResourceNotFoundException("Customer not found with id: 999"))
                .when(customerService).deleteCustomer(999L);
        
        // Act & Assert
        mockMvc.perform(delete("/api/customers/999"))
                .andExpect(status().isNotFound());
        
        verify(customerService, times(1)).deleteCustomer(999L);
    }
}
