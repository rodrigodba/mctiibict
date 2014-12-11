package pt.utl.ist.repox.reports;

/**
 * Created to project REPOX.
 * User: Edmundo
 * Date: 04/07/12
 * Time: 16:55
 */
public class LogElement {

    private String id;
    private String value;
    protected LogEntryType type;

    private String errorCause;
    protected String failedId;

    public LogElement(String id, String value) {
        this.id = id;
        this.value = value;
        this.type = LogEntryType.NORMAL;
    }

    // Error
    public LogElement(String id, String failedId, String errorCause) {
        this.id = id;
        this.errorCause = errorCause;
        this.failedId = failedId;
        this.type = LogEntryType.ERROR;
    }

    public String getId() {
        return id;
    }

    public String getValue() {
        return value;
    }
    public String getFailedId() {
        return failedId;
    }

    public String getErrorCause() {
        return errorCause;
    }

    public LogEntryType getType() {
        return type;
    }
}
