package git.yannynz.organizadorproducao.model.dto; 

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileWatcherPong {
    private boolean ok;
    private String instanceId; 
    private String ts;          
    private String version;      

    public FileWatcherPong() {}

    public FileWatcherPong(boolean ok, String instanceId, String ts, String version) {
        this.ok = ok;
        this.instanceId = instanceId;
        this.ts = ts;
        this.version = version;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getTs() { return ts; }
    public void setTs(String ts) { this.ts = ts; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}

