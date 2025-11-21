package org.vericrop.gui.events;

/**
 * Event published when a report is generated and ready.
 */
public class ReportReady extends DomainEvent {
    private final String reportId;
    private final String reportType;
    private final String filePath;
    private final String fileName;
    
    public ReportReady(String reportId, String reportType, String filePath, String fileName) {
        super();
        this.reportId = reportId;
        this.reportType = reportType;
        this.filePath = filePath;
        this.fileName = fileName;
    }
    
    public String getReportId() {
        return reportId;
    }
    
    public String getReportType() {
        return reportType;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    @Override
    public String toString() {
        return "ReportReady{" +
               "reportId='" + reportId + '\'' +
               ", reportType='" + reportType + '\'' +
               ", filePath='" + filePath + '\'' +
               ", fileName='" + fileName + '\'' +
               ", " + super.toString() +
               '}';
    }
}
