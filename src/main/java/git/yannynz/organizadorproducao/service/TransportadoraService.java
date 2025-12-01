package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Transportadora;
import git.yannynz.organizadorproducao.repository.TransportadoraRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class TransportadoraService {

    private final TransportadoraRepository repo;

    public TransportadoraService(TransportadoraRepository repo) {
        this.repo = repo;
    }

    public Page<Transportadora> search(String query, Pageable pageable) {
        return repo.search(query, pageable);
    }

    public Optional<Transportadora> findById(Long id) {
        return repo.findById(id);
    }

    @Transactional
    public Transportadora create(Transportadora t) {
        t.setId(null);
        if (t.getNomeOficial() == null || t.getNomeOficial().isBlank()) {
            throw new IllegalArgumentException("Nome oficial é obrigatório");
        }
        t.setNomeNormalizado(normalize(t.getNomeOficial()));
        return repo.save(t);
    }

    @Transactional
    public Transportadora update(Long id, Transportadora update) {
        return repo.findById(id).map(existing -> {
            if (update.getNomeOficial() != null) {
                existing.setNomeOficial(update.getNomeOficial());
                existing.setNomeNormalizado(normalize(update.getNomeOficial()));
            }
            if (update.getApelidos() != null) existing.setApelidos(update.getApelidos());
            if (update.getLocalizacao() != null) existing.setLocalizacao(update.getLocalizacao());
            if (update.getHorarioFuncionamento() != null) existing.setHorarioFuncionamento(update.getHorarioFuncionamento());
            if (update.getPadraoEntrega() != null) existing.setPadraoEntrega(update.getPadraoEntrega());
            if (update.getObservacoes() != null) existing.setObservacoes(update.getObservacoes());
            if (update.getAtivo() != null) existing.setAtivo(update.getAtivo());
            return repo.save(existing);
        }).orElseThrow(() -> new RuntimeException("Transportadora não encontrada"));
    }

    private String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase().trim();
    }
}
