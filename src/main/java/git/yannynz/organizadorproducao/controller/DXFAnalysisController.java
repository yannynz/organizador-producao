package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.model.dto.DXFAnalysisRequestDTO;
import git.yannynz.organizadorproducao.model.dto.DXFAnalysisRequestResponse;
import git.yannynz.organizadorproducao.model.dto.DXFAnalysisView;
import git.yannynz.organizadorproducao.service.DXFAnalysisRequestPublisher;
import git.yannynz.organizadorproducao.service.DXFAnalysisService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dxf-analysis")
public class DXFAnalysisController {

    private final DXFAnalysisService analysisService;
    private final DXFAnalysisRequestPublisher requestPublisher;

    public DXFAnalysisController(DXFAnalysisService analysisService,
                                 DXFAnalysisRequestPublisher requestPublisher) {
        this.analysisService = analysisService;
        this.requestPublisher = requestPublisher;
    }

    @GetMapping("/order/{orderNr}")
    public ResponseEntity<DXFAnalysisView> getLatestByOrder(@PathVariable String orderNr) {
        return analysisService.findLatestByOrderNr(orderNr)
                .map(analysisService::toView)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/order/{orderNr}/history")
    public ResponseEntity<List<DXFAnalysisView>> listHistory(@PathVariable String orderNr,
                                                             @RequestParam(name = "limit", defaultValue = "5") int limit) {
        List<DXFAnalysisView> history = analysisService.listRecentByOrder(orderNr, limit);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{analysisId}")
    public ResponseEntity<DXFAnalysisView> getByAnalysisId(@PathVariable String analysisId) {
        return analysisService.findByAnalysisId(analysisId)
                .map(analysisService::toView)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{analysisId}/image")
    public ResponseEntity<?> getImage(@PathVariable String analysisId) {
        return analysisService.loadAnalysisImage(analysisId);
    }

    @PostMapping("/request")
    public ResponseEntity<DXFAnalysisRequestResponse> requestAnalysis(@Valid @RequestBody DXFAnalysisRequestDTO request) {
        String analysisId = requestPublisher.publish(request);
        DXFAnalysisRequestResponse response = new DXFAnalysisRequestResponse(analysisId, request.orderNumber());
        return ResponseEntity.accepted().body(response);
    }
}
