package io.github.evoschema.processor.exception;


public class EvoSchemaException extends RuntimeException
{
    private static final long serialVersionUID = -3431999489674930350L;

    public enum ProcesssError {
        PRE_DLL_ERROR,
        DML_SQL_ERROR,
        DML_SCRIPT_ERROR,
        DML_CONFIRM,
        POST_DLL_ERROR,
        GBID_ERROR,
        COMPONENT_ANNOTATION_ERROR,
    }
    
    private ProcesssError reason;
    
    public EvoSchemaException(ProcesssError reason)
    {
        super();
        this.reason = reason;
    }

    public EvoSchemaException(ProcesssError reason, String msg)
    {
        super(msg);
        this.reason = reason;
    }

    public EvoSchemaException(ProcesssError reason, Throwable e)
    {
        super(e);
        this.reason = reason;
    }

    public EvoSchemaException(ProcesssError reason, String msg, Throwable e)
    {
        super(msg, e);
        this.reason = reason;
    }

    /**
     * @return the reason
     */
    public ProcesssError getReason() {
        return reason;
    }

    /**
     * @param reason the reason to set
     */
    public void setReason(ProcesssError reason) {
        this.reason = reason;
    }
}

