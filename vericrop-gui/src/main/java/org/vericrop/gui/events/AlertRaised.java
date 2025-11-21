package org.vericrop.gui.events;

/**
 * Event published when an alert is raised.
 */
public class AlertRaised extends DomainEvent {
    public enum Severity {
        INFO, WARNING, ERROR, CRITICAL
    }
    
    private final String title;
    private final String message;
    private final Severity severity;
    private final String source;
    
    public AlertRaised(String title, String message, Severity severity, String source) {
        super();
        this.title = title;
        this.message = message;
        this.severity = severity;
        this.source = source;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Severity getSeverity() {
        return severity;
    }
    
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return "AlertRaised{" +
               "title='" + title + '\'' +
               ", message='" + message + '\'' +
               ", severity=" + severity +
               ", source='" + source + '\'' +
               ", " + super.toString() +
               '}';
    }
}
