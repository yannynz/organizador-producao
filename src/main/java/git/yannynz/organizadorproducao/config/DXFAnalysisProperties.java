package git.yannynz.organizadorproducao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.dxf.analysis")
public class DXFAnalysisProperties {

    /**
     * Queue that receives requests for DXF analysis.
     */
    private String requestQueue = "facas.analysis.request";

    /**
     * Queue where DXF analysis results are published by FileWatcherApp.
     */
    private String resultQueue = "facas.analysis.result";

    /**
     * WebSocket topic used to broadcast fresh analysis data.
     */
    private String websocketTopic = "/topic/dxf-analysis";

    /**
     * Optional base URL (or prefix) to serve rendered DXF images when the storage URI is absent.
     */
    private String imageBaseUrl = "";

    /**
     * Optional list of local directories from which rendered images can be served.
     * When empty, the backend will not attempt to load files from the local filesystem.
     */
    private List<String> imageLocalRoots = new ArrayList<>();

    /**
     * Regex used to extract order number (NR/CL) when not provided explicitly.
     */
    private String orderNumberPattern = "(?i)(?:NR|CL)\\s*(\\d+)";

    public String getRequestQueue() {
        return requestQueue;
    }

    public void setRequestQueue(String requestQueue) {
        this.requestQueue = requestQueue;
    }

    public String getResultQueue() {
        return resultQueue;
    }

    public void setResultQueue(String resultQueue) {
        this.resultQueue = resultQueue;
    }

    public String getWebsocketTopic() {
        return websocketTopic;
    }

    public void setWebsocketTopic(String websocketTopic) {
        this.websocketTopic = websocketTopic;
    }

    public String getImageBaseUrl() {
        return imageBaseUrl;
    }

    public void setImageBaseUrl(String imageBaseUrl) {
        this.imageBaseUrl = imageBaseUrl;
    }

    public List<String> getImageLocalRoots() {
        return imageLocalRoots;
    }

    public void setImageLocalRoots(List<String> imageLocalRoots) {
        this.imageLocalRoots = imageLocalRoots != null ? imageLocalRoots : new ArrayList<>();
    }

    public String getOrderNumberPattern() {
        return orderNumberPattern;
    }

    public void setOrderNumberPattern(String orderNumberPattern) {
        this.orderNumberPattern = orderNumberPattern;
    }
}
