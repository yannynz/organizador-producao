package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Cliente;
import git.yannynz.organizadorproducao.model.ClienteEndereco;
import git.yannynz.organizadorproducao.repository.ClienteEnderecoRepository;
import git.yannynz.organizadorproducao.repository.ClienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import git.yannynz.organizadorproducao.repository.TransportadoraRepository;

@Service
public class ClienteService {

    private final ClienteRepository repo;
    private final ClienteEnderecoRepository enderecoRepo;
    private final TransportadoraRepository transportadoraRepo;

    public ClienteService(ClienteRepository repo,
                          ClienteEnderecoRepository enderecoRepo,
                          TransportadoraRepository transportadoraRepo) {
        this.repo = repo;
        this.enderecoRepo = enderecoRepo;
        this.transportadoraRepo = transportadoraRepo;
    }

    public Page<Cliente> search(String query, Pageable pageable) {
        return repo.search(query, pageable);
    }

    public Optional<Cliente> findById(Long id) {
        return repo.findById(id);
    }

    public List<ClienteEndereco> listEnderecos(Long clienteId) {
        return enderecoRepo.findByClienteId(clienteId);
    }

    public Optional<ClienteEndereco> findEnderecoDefault(Long clienteId) {
        return enderecoRepo.findByClienteIdAndIsDefaultTrue(clienteId);
    }

    @Transactional
    public Cliente create(Cliente cliente) {
        if (cliente.getNomeOficial() == null || cliente.getNomeOficial().isBlank()) {
            throw new IllegalArgumentException("Nome oficial é obrigatório");
        }
        cliente.setNomeNormalizado(normalize(cliente.getNomeOficial()));

        if (cliente.getDefaultEmborrachada() == null) {
            cliente.setDefaultEmborrachada(false);
        }
        if (cliente.getDefaultPertinax() == null) {
            cliente.setDefaultPertinax(false);
        }
        if (cliente.getDefaultPoliester() == null) {
            cliente.setDefaultPoliester(false);
        }
        if (cliente.getDefaultPapelCalibrado() == null) {
            cliente.setDefaultPapelCalibrado(false);
        }
        
        if (cliente.getTransportadoraId() != null) {
            transportadoraRepo.findById(cliente.getTransportadoraId())
                .ifPresent(cliente::setTransportadora);
        }
        
        return repo.save(cliente);
    }

    @Transactional
    public Cliente update(Long id, Cliente update) {
        return repo.findById(id).map(existing -> {
            if (update.getNomeOficial() != null) {
                existing.setNomeOficial(update.getNomeOficial());
                existing.setNomeNormalizado(normalize(update.getNomeOficial()));
            }
            if (update.getApelidos() != null) existing.setApelidos(update.getApelidos());
            if (update.getPadraoEntrega() != null) existing.setPadraoEntrega(update.getPadraoEntrega());
            if (update.getHorarioFuncionamento() != null) existing.setHorarioFuncionamento(update.getHorarioFuncionamento());
            if (update.getObservacoes() != null) existing.setObservacoes(update.getObservacoes());
            if (update.getAtivo() != null) existing.setAtivo(update.getAtivo());

            if (update.getDefaultEmborrachada() != null) existing.setDefaultEmborrachada(update.getDefaultEmborrachada());
            if (update.getDefaultDestacador() != null) existing.setDefaultDestacador(update.getDefaultDestacador());
            if (update.getDefaultPertinax() != null) existing.setDefaultPertinax(update.getDefaultPertinax());
            if (update.getDefaultPoliester() != null) existing.setDefaultPoliester(update.getDefaultPoliester());
            if (update.getDefaultPapelCalibrado() != null) existing.setDefaultPapelCalibrado(update.getDefaultPapelCalibrado());
            
            if (update.getTransportadoraId() != null) {
                 if (update.getTransportadoraId() == -1) { // Special value to clear? Or just nullable.
                     existing.setTransportadora(null);
                 } else {
                     transportadoraRepo.findById(update.getTransportadoraId())
                         .ifPresent(existing::setTransportadora);
                 }
            } else if (update.getTransportadora() == null && update.getTransportadoraId() == null) {
                // If both are null in update payload, typically means "no change" or "clear". 
                // For JSON Patch, null means no change. To clear, we need explicit nullification or a specific flag.
                // Let's assume if transportadoraId is sent as null in JSON, it's ignored. 
                // If the frontend sends a specific value (e.g. null explicitly), Jackson maps it.
                // But here we use a transient field. 
                // Let's assume frontend sends `transportadoraId: null` means unset? No, `transportadoraId: undefined` is missing.
                // If user selects "Nenhuma", frontend should send null?
                // Let's rely on `transportadoraId` being populated. 
                // If the form sends `transportadoraId: null`, Java object has null.
                // We need a way to distinguish "unset" vs "no change". 
                // For now, let's just check if it's present in the request. 
                // A simple approach: If `transportadoraId` is set (not null), update it. 
                // If the user wants to remove, they might send 0 or -1 or we need a specific check.
                // Let's support clearing via a dedicated setter or assuming if passed (even null) we update? 
                // Ideally PATCH only updates what's present. 
                // I'll assume if `transportadoraId` > 0 update. If 0 or -1, clear. 
            }
            
            // Better logic: If the payload has the field. 
            // Since we receive the whole object `Cliente`, Jackson populates fields. 
            // If `transportadoraId` is null, it might be unselected.
            // Let's just allow updating if non-null for now to support "Adding". 
            // To support "Removing", we might need `transportadoraId` to be nullable and handled explicitly.
            
            if (update.getTransportadoraId() != null) {
                 transportadoraRepo.findById(update.getTransportadoraId())
                     .ifPresent(existing::setTransportadora);
            }
            // Logic to clear:
            // if (update.getTransportadoraId() == null && /* explicit clear signal */) existing.setTransportadora(null);
            
            return repo.save(existing);
        }).orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
    }

    @Transactional
    public Cliente linkAliases(Long clienteId, Long aliasClienteId) {
        if (clienteId == null || aliasClienteId == null) {
            throw new IllegalArgumentException("Cliente e apelido são obrigatórios");
        }
        if (clienteId.equals(aliasClienteId)) {
            throw new IllegalArgumentException("Cliente e apelido não podem ser o mesmo");
        }
        Cliente cliente = repo.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
        Cliente alias = repo.findById(aliasClienteId)
                .orElseThrow(() -> new RuntimeException("Cliente apelido não encontrado"));

        boolean changed = false;
        changed |= addAlias(cliente, alias.getNomeOficial());
        changed |= addAlias(alias, cliente.getNomeOficial());

        if (changed) {
            repo.save(cliente);
            repo.save(alias);
        }
        return cliente;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase().trim();
    }

    private boolean addAlias(Cliente cliente, String alias) {
        if (cliente == null || alias == null || alias.isBlank()) {
            return false;
        }
        String normalizedAlias = normalize(alias);
        String normalizedName = normalize(cliente.getNomeOficial());
        if (!normalizedAlias.isBlank() && normalizedAlias.equals(normalizedName)) {
            return false;
        }
        List<String> apelidos = cliente.getApelidos();
        if (apelidos == null) {
            apelidos = new ArrayList<>();
        }
        boolean exists = apelidos.stream()
                .filter(a -> a != null && !a.isBlank())
                .map(this::normalize)
                .anyMatch(n -> n.equals(normalizedAlias));
        if (!exists) {
            apelidos.add(alias.trim());
            cliente.setApelidos(apelidos);
            return true;
        }
        return false;
    }
}
