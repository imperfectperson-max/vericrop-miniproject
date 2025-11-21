package org.vericrop.gui.events;

/**
 * Event published when analytics data is updated.
 */
public class AnalyticsUpdated extends DomainEvent {
    private final String dataType;
    private final Object data;
    
    public AnalyticsUpdated(String dataType, Object data) {
        super();
        this.dataType = dataType;
        this.data = data;
    }
    
    public String getDataType() {
        return dataType;
    }
    
    public Object getData() {
        return data;
    }
    
    @Override
    public String toString() {
        return "AnalyticsUpdated{" +
               "dataType='" + dataType + '\'' +
               ", " + super.toString() +
               '}';
    }
}
