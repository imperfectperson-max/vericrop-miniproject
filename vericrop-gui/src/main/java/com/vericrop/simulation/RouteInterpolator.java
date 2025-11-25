package com.vericrop.simulation;

/**
 * Interpolates position and environmental data along a delivery route.
 * Provides smooth transitions between waypoints for animation.
 */
public class RouteInterpolator {
    
    private final double originLat;
    private final double originLon;
    private final double destLat;
    private final double destLon;
    private final String originName;
    private final String destName;
    private final double baseTemperature;
    private final double baseHumidity;
    
    // Temperature and humidity variation parameters
    private static final double TEMP_VARIATION_AMPLITUDE = 1.0;  // +/- 1°C variation
    private static final double HUMIDITY_VARIATION_AMPLITUDE = 5.0;  // +/- 5% variation
    
    /**
     * Create an interpolator for a route between two points.
     */
    public RouteInterpolator(double originLat, double originLon, String originName,
                             double destLat, double destLon, String destName,
                             double baseTemperature, double baseHumidity) {
        this.originLat = originLat;
        this.originLon = originLon;
        this.originName = originName;
        this.destLat = destLat;
        this.destLon = destLon;
        this.destName = destName;
        this.baseTemperature = baseTemperature;
        this.baseHumidity = baseHumidity;
    }
    
    /**
     * Interpolate position at a given progress.
     * @param progress Progress as fraction (0.0 to 1.0)
     * @return Interpolated position data
     */
    public Position interpolatePosition(double progress) {
        // Clamp progress to valid range
        progress = Math.max(0.0, Math.min(1.0, progress));
        
        // Linear interpolation of coordinates
        double lat = originLat + (destLat - originLat) * progress;
        double lon = originLon + (destLon - originLon) * progress;
        
        // Determine location name based on progress
        String locationName = determineLocationName(progress);
        
        return new Position(lat, lon, locationName);
    }
    
    /**
     * Interpolate environmental conditions at a given progress.
     * Adds realistic variation based on position in the route.
     * @param progress Progress as fraction (0.0 to 1.0)
     * @return Interpolated environmental data
     */
    public Environment interpolateEnvironment(double progress) {
        // Add sinusoidal variation to simulate environmental changes during transit
        // Temperature tends to be more stable at endpoints, varies more in middle
        double tempVariation = TEMP_VARIATION_AMPLITUDE * 
            Math.sin(progress * Math.PI) * 
            (0.5 + 0.5 * Math.sin(progress * 4 * Math.PI));
        
        double humidityVariation = HUMIDITY_VARIATION_AMPLITUDE *
            Math.cos(progress * Math.PI * 2);
        
        double temperature = baseTemperature + tempVariation;
        double humidity = Math.max(0, Math.min(100, baseHumidity + humidityVariation));
        
        return new Environment(temperature, humidity);
    }
    
    /**
     * Get interpolated data at a given progress.
     * @param progress Progress as fraction (0.0 to 1.0)
     * @return Combined position and environmental data
     */
    public InterpolatedData interpolate(double progress) {
        Position position = interpolatePosition(progress);
        Environment environment = interpolateEnvironment(progress);
        return new InterpolatedData(position, environment);
    }
    
    /**
     * Determine a descriptive location name based on progress.
     */
    private String determineLocationName(double progress) {
        if (progress < 0.05) {
            return originName;
        } else if (progress < 0.15) {
            return "Departing " + originName;
        } else if (progress < 0.30) {
            return "En route - Mile 10";
        } else if (progress < 0.45) {
            return "En route - Highway";
        } else if (progress < 0.55) {
            return "Midpoint - Rest Area";
        } else if (progress < 0.70) {
            return "En route - Exit Approaching";
        } else if (progress < 0.85) {
            return "Approaching " + destName;
        } else if (progress < 0.95) {
            return "Entering " + destName;
        } else {
            return destName;
        }
    }
    
    /**
     * Calculate the total distance in kilometers between origin and destination.
     * Uses Haversine formula.
     */
    public double calculateDistanceKm() {
        final double R = 6371; // Earth's radius in km
        
        double lat1Rad = Math.toRadians(originLat);
        double lat2Rad = Math.toRadians(destLat);
        double dLat = Math.toRadians(destLat - originLat);
        double dLon = Math.toRadians(destLon - originLon);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * Represents a position on the route.
     */
    public static class Position {
        private final double latitude;
        private final double longitude;
        private final String locationName;
        
        public Position(double latitude, double longitude, String locationName) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.locationName = locationName;
        }
        
        public double getLatitude() {
            return latitude;
        }
        
        public double getLongitude() {
            return longitude;
        }
        
        public String getLocationName() {
            return locationName;
        }
        
        @Override
        public String toString() {
            return String.format("Position{lat=%.6f, lon=%.6f, name='%s'}",
                                latitude, longitude, locationName);
        }
    }
    
    /**
     * Represents environmental conditions.
     */
    public static class Environment {
        private final double temperature;
        private final double humidity;
        
        public Environment(double temperature, double humidity) {
            this.temperature = temperature;
            this.humidity = humidity;
        }
        
        public double getTemperature() {
            return temperature;
        }
        
        public double getHumidity() {
            return humidity;
        }
        
        @Override
        public String toString() {
            return String.format("Environment{temp=%.1f°C, humidity=%.1f%%}",
                                temperature, humidity);
        }
    }
    
    /**
     * Combined position and environmental data.
     */
    public static class InterpolatedData {
        private final Position position;
        private final Environment environment;
        
        public InterpolatedData(Position position, Environment environment) {
            this.position = position;
            this.environment = environment;
        }
        
        public Position getPosition() {
            return position;
        }
        
        public Environment getEnvironment() {
            return environment;
        }
        
        public double getLatitude() {
            return position.getLatitude();
        }
        
        public double getLongitude() {
            return position.getLongitude();
        }
        
        public String getLocationName() {
            return position.getLocationName();
        }
        
        public double getTemperature() {
            return environment.getTemperature();
        }
        
        public double getHumidity() {
            return environment.getHumidity();
        }
        
        @Override
        public String toString() {
            return "InterpolatedData{" + position + ", " + environment + "}";
        }
    }
    
    /**
     * Builder for creating RouteInterpolator instances.
     */
    public static class Builder {
        private double originLat;
        private double originLon;
        private String originName = "Origin";
        private double destLat;
        private double destLon;
        private String destName = "Destination";
        private double baseTemperature = 4.0;  // Default cold-chain temp
        private double baseHumidity = 65.0;    // Default humidity
        
        public Builder origin(double lat, double lon, String name) {
            this.originLat = lat;
            this.originLon = lon;
            this.originName = name;
            return this;
        }
        
        public Builder destination(double lat, double lon, String name) {
            this.destLat = lat;
            this.destLon = lon;
            this.destName = name;
            return this;
        }
        
        public Builder baseTemperature(double temperature) {
            this.baseTemperature = temperature;
            return this;
        }
        
        public Builder baseHumidity(double humidity) {
            this.baseHumidity = humidity;
            return this;
        }
        
        public RouteInterpolator build() {
            return new RouteInterpolator(
                originLat, originLon, originName,
                destLat, destLon, destName,
                baseTemperature, baseHumidity
            );
        }
    }
}
