package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.model.dto.OpImportRequestDTO;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import git.yannynz.organizadorproducao.repository.OpImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OpImportService {

  private final OpImportRepository repo;
  private final OrderRepository facaRepo;
  private final ObjectMapper mapper;

  @Transactional
  public void importar(OpImportRequestDTO req) {
    OpImport op = repo.findByNumeroOp(req.numeroOp()).orElseGet(OpImport::new);

    op.setNumeroOp(req.numeroOp());
    op.setCodigoProduto(req.codigoProduto());
    op.setDescricaoProduto(req.descricaoProduto());
    op.setCliente(req.cliente());
    if (req.dataOp() != null && !req.dataOp().isBlank()) {
      op.setDataOp(LocalDate.parse(req.dataOp()));
    }
    op.setEmborrachada(Boolean.TRUE.equals(req.emborrachada()));
    op.setSharePath(req.sharePath());
    if (req.materiais() != null) {
      op.setMateriais(mapper.valueToTree(req.materiais()));
    }

    // Vínculo OP -> Faca pela NR
    facaRepo.findByNr(req.numeroOp()).ifPresent(f -> {
      op.setFacaId(f.getId());
      if (Boolean.TRUE.equals(req.emborrachada())) {
        String obs = Optional.ofNullable(f.getObservacao()).orElse("");
        if (!obs.toLowerCase().contains("emborrachada")) {
          f.setObservacao(obs.isBlank() ? "emborrachada" : obs + "; emborrachada");
          facaRepo.save(f);
        }
      }
    });

    repo.save(op);
  }

  @Transactional
  public void onFacaMontada(Long facaId) {
    boolean emborrachada = repo.findTopByFacaIdOrderByCreatedAtDesc(facaId)
        .map(OpImport::isEmborrachada).orElse(false);
    if (!emborrachada) {
      var f = facaRepo.findById(facaId).orElseThrow();
      f.setStatus(2);
      facaRepo.save(f);
    }
  }
}

