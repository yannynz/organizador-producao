package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.model.dto.OpImportRequestDTO;
import git.yannynz.organizadorproducao.repository.OpImportRepository;
import git.yannynz.organizadorproducao.service.OpImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/ops", "/api/ops"}) 
public class OpImportController {

  private final OpImportService service;
  private final OpImportRepository repo;

  public OpImportController(OpImportService service, OpImportRepository repo) {
    this.service = service;
    this.repo = repo;
  }

  @PostMapping("/import")
  public ResponseEntity<Void> importar(@RequestBody OpImportRequestDTO req) {
    service.importar(req); // idempotente por numeroOp
    return ResponseEntity.accepted().build();
  }

  @PatchMapping("/{id}/vincular-faca/{facaId}")
  public ResponseEntity<Void> vincular(@PathVariable Long id, @PathVariable Long facaId) {
    OpImport op = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    op.setFacaId(facaId);
    repo.save(op);
    return ResponseEntity.noContent().build();
  }
}

