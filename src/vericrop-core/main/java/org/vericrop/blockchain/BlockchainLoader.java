package org.vericrop.blockchain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;

/**
 * Runtime loader for the external blockchain.jar.
 * 
 * This utility class loads the blockchain.jar at runtime using URLClassLoader,
 * avoiding hard compile-time dependencies on classes inside the jar.
 * It provides reflective access to classes and methods within the jar.
 */
public class BlockchainLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(BlockchainLoader.class);
    
    private static final String JAR_FILENAME = "blockchain.jar";
    private static final String[] SEARCH_PATHS = {
        "libs",
        "dist",
        ".",
        "../libs",
        "../dist"
    };
    
    private static volatile BlockchainLoader instance;
    private static final Object lock = new Object();
    
    private URLClassLoader classLoader;
    private boolean available;
    private String loadedJarPath;
    
    /**
     * Private constructor - use getInstance() for singleton access.
     */
    private BlockchainLoader() {
        this.available = false;
        initialize();
    }
    
    /**
     * Get the singleton instance of BlockchainLoader.
     * 
     * @return the BlockchainLoader instance
     */
    public static BlockchainLoader getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new BlockchainLoader();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize the loader by finding and loading the blockchain.jar.
     */
    private void initialize() {
        File jarFile = findJarFile();
        if (jarFile == null) {
            logger.warn("blockchain.jar not found in any of the search paths: {}", 
                String.join(", ", SEARCH_PATHS));
            return;
        }
        
        try {
            URL jarUrl = jarFile.toURI().toURL();
            this.classLoader = new URLClassLoader(
                new URL[]{jarUrl},
                getClass().getClassLoader()
            );
            this.available = true;
            this.loadedJarPath = jarFile.getAbsolutePath();
            logger.info("Successfully loaded blockchain.jar from: {}", loadedJarPath);
        } catch (Exception e) {
            logger.error("Failed to load blockchain.jar: {}", e.getMessage(), e);
            this.available = false;
        }
    }
    
    /**
     * Find the blockchain.jar file in the search paths.
     * 
     * @return the jar File if found, null otherwise
     */
    private File findJarFile() {
        // First try to find based on working directory
        for (String searchPath : SEARCH_PATHS) {
            File jarFile = new File(searchPath, JAR_FILENAME);
            if (jarFile.exists() && jarFile.isFile()) {
                logger.debug("Found blockchain.jar at: {}", jarFile.getAbsolutePath());
                return jarFile;
            }
        }
        
        // Try to find relative to the class location
        try {
            String classPath = getClass().getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            File classDir = new File(classPath).getParentFile();
            if (classDir != null) {
                for (String searchPath : SEARCH_PATHS) {
                    File jarFile = new File(classDir, searchPath + "/" + JAR_FILENAME);
                    if (jarFile.exists() && jarFile.isFile()) {
                        logger.debug("Found blockchain.jar at: {}", jarFile.getAbsolutePath());
                        return jarFile;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not determine class location: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Check if the blockchain.jar was loaded successfully.
     * 
     * @return true if the jar is available and loaded, false otherwise
     */
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * Get the path to the loaded jar file.
     * 
     * @return the absolute path to the loaded jar, or null if not loaded
     */
    public String getLoadedJarPath() {
        return loadedJarPath;
    }
    
    /**
     * Load a class from the blockchain.jar.
     * 
     * @param className the fully qualified class name
     * @return Optional containing the Class if found, empty otherwise
     */
    public Optional<Class<?>> loadClass(String className) {
        if (!available || classLoader == null) {
            logger.warn("Cannot load class '{}': blockchain.jar not available", className);
            return Optional.empty();
        }
        
        try {
            Class<?> clazz = classLoader.loadClass(className);
            logger.debug("Successfully loaded class: {}", className);
            return Optional.of(clazz);
        } catch (ClassNotFoundException e) {
            logger.warn("Class not found in blockchain.jar: {}", className);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error loading class '{}': {}", className, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Create a new instance of a class from the blockchain.jar.
     * 
     * @param className the fully qualified class name
     * @param paramTypes the constructor parameter types
     * @param args the constructor arguments
     * @return Optional containing the new instance if successful, empty otherwise
     */
    public Optional<Object> createInstance(String className, Class<?>[] paramTypes, Object[] args) {
        Optional<Class<?>> classOpt = loadClass(className);
        if (classOpt.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            Class<?> clazz = classOpt.get();
            Constructor<?> constructor = clazz.getConstructor(paramTypes);
            Object instance = constructor.newInstance(args);
            logger.debug("Successfully created instance of: {}", className);
            return Optional.of(instance);
        } catch (NoSuchMethodException e) {
            logger.warn("Constructor not found for class '{}' with given parameter types", className);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error creating instance of '{}': {}", className, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Create a new instance of a class using the default constructor.
     * 
     * @param className the fully qualified class name
     * @return Optional containing the new instance if successful, empty otherwise
     */
    public Optional<Object> createInstance(String className) {
        return createInstance(className, new Class<?>[0], new Object[0]);
    }
    
    /**
     * Invoke a static method on a class from the blockchain.jar.
     * 
     * @param className the fully qualified class name
     * @param methodName the method name
     * @param paramTypes the method parameter types
     * @param args the method arguments
     * @return Optional containing the result if successful, empty otherwise
     */
    public Optional<Object> invokeStatic(String className, String methodName, 
            Class<?>[] paramTypes, Object[] args) {
        Optional<Class<?>> classOpt = loadClass(className);
        if (classOpt.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            Class<?> clazz = classOpt.get();
            Method method = clazz.getMethod(methodName, paramTypes);
            Object result = method.invoke(null, args);
            logger.debug("Successfully invoked static method {}.{}", className, methodName);
            return Optional.ofNullable(result);
        } catch (NoSuchMethodException e) {
            logger.warn("Static method '{}' not found in class '{}'", methodName, className);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error invoking static method '{}.{}': {}", 
                className, methodName, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Invoke an instance method on an object from the blockchain.jar.
     * 
     * @param instance the object instance
     * @param methodName the method name
     * @param paramTypes the method parameter types
     * @param args the method arguments
     * @return Optional containing the result if successful, empty otherwise
     */
    public Optional<Object> invoke(Object instance, String methodName, 
            Class<?>[] paramTypes, Object[] args) {
        if (instance == null) {
            logger.warn("Cannot invoke method '{}': instance is null", methodName);
            return Optional.empty();
        }
        
        if (!available || classLoader == null) {
            logger.warn("Cannot invoke method '{}': blockchain.jar not available", methodName);
            return Optional.empty();
        }
        
        try {
            Method method = instance.getClass().getMethod(methodName, paramTypes);
            Object result = method.invoke(instance, args);
            logger.debug("Successfully invoked method '{}' on {}", 
                methodName, instance.getClass().getName());
            return Optional.ofNullable(result);
        } catch (NoSuchMethodException e) {
            logger.warn("Method '{}' not found on class '{}'", 
                methodName, instance.getClass().getName());
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error invoking method '{}' on '{}': {}", 
                methodName, instance.getClass().getName(), e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Invoke a method reflectively using class name.
     * Supports both static methods (pass null for instance) and instance methods.
     * 
     * @param className the fully qualified class name
     * @param methodName the method name
     * @param paramTypes the method parameter types (can be null for no parameters)
     * @param args the method arguments (can be null for no arguments)
     * @return Optional containing the result if successful, empty otherwise
     */
    public Optional<Object> invoke(String className, String methodName, 
            Class<?>[] paramTypes, Object[] args) {
        // Normalize null arrays
        Class<?>[] types = paramTypes != null ? paramTypes : new Class<?>[0];
        Object[] arguments = args != null ? args : new Object[0];
        
        // For static method invocation
        return invokeStatic(className, methodName, types, arguments);
    }
    
    /**
     * Close the class loader and release resources.
     */
    public void close() {
        if (classLoader != null) {
            try {
                classLoader.close();
                logger.info("BlockchainLoader closed successfully");
            } catch (Exception e) {
                logger.error("Error closing BlockchainLoader: {}", e.getMessage(), e);
            } finally {
                classLoader = null;
                available = false;
            }
        }
    }
    
    /**
     * Reset the singleton instance (useful for testing).
     * After calling this, the next call to getInstance() will create a new instance.
     */
    public static void resetInstance() {
        synchronized (lock) {
            if (instance != null) {
                instance.close();
                instance = null;
            }
        }
    }
}
