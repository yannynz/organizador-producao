package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Cliente;
import git.yannynz.organizadorproducao.model.ClienteEndereco;
import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.model.dto.EnderecoSugeridoDTO;
import git.yannynz.organizadorproducao.model.dto.OpImportRequestDTO;
import git.yannynz.organizadorproducao.repository.ClienteEnderecoRepository;
import git.yannynz.organizadorproducao.repository.ClienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class ClienteAutoEnrichmentService {

    private final ClienteRepository clienteRepo;
    private final ClienteEnderecoRepository enderecoRepo;
    private final Logger log = LoggerFactory.getLogger(ClienteAutoEnrichmentService.class);

    public ClienteAutoEnrichmentService(
            ClienteRepository clienteRepo,
            ClienteEnderecoRepository enderecoRepo) {
        this.clienteRepo = clienteRepo;
        this.enderecoRepo = enderecoRepo;
    }

    @Transactional
    public ClienteEndereco upsertFromOp(OpImport op, OpImportRequestDTO dto) {
        if (dto.getClienteNomeOficial() == null || dto.getClienteNomeOficial().isBlank()) {
            return null;
        }

        String nomeNormalizado = normalizar(dto.getClienteNomeOficial());

        Cliente cliente = clienteRepo.findByNomeNormalizado(nomeNormalizado)
                .orElseGet(() -> createCliente(dto, nomeNormalizado));

        boolean dirty = false;
        
        // Update existing data if new data is available
        if (dto.getPadraoEntregaSugerido() != null && !dto.getPadraoEntregaSugerido().equals(cliente.getPadraoEntrega())) {
            cliente.setPadraoEntrega(dto.getPadraoEntregaSugerido());
            dirty = true;
        }

        if (dto.getCnpjCpf() != null && !dto.getCnpjCpf().equals(cliente.getCnpjCpf())) {
            cliente.setCnpjCpf(dto.getCnpjCpf());
            dirty = true;
        }
        
        if (dto.getInscricaoEstadual() != null && !dto.getInscricaoEstadual().equals(cliente.getInscricaoEstadual())) {
            cliente.setInscricaoEstadual(dto.getInscricaoEstadual());
            dirty = true;
        }

        if (dto.getTelefone() != null && !dto.getTelefone().equals(cliente.getTelefone())) {
            cliente.setTelefone(dto.getTelefone());
            dirty = true;
        }

        if (dto.getEmailContato() != null && !dto.getEmailContato().equals(cliente.getEmailContato())) {
            cliente.setEmailContato(dto.getEmailContato());
            dirty = true;
        }
        
        if (dto.getDataUltimoServicoSugerida() != null) {
            try {
                ZonedDateTime zdt;
                try {
                    zdt = ZonedDateTime.parse(dto.getDataUltimoServicoSugerida());
                } catch (Exception e) {
                    // Fallback: LocalDateTime (no offset) assuming system/default zone
                    zdt = java.time.LocalDateTime.parse(dto.getDataUltimoServicoSugerida()).atZone(ZoneId.systemDefault());
                }
                OffsetDateTime odt = zdt.toOffsetDateTime();
                
                if (cliente.getUltimoServicoEm() == null || odt.isAfter(cliente.getUltimoServicoEm())) {
                    cliente.setUltimoServicoEm(odt);
                    dirty = true;
                }
            } catch (Exception e) {
                log.warn("Falha ao parsear dataUltimoServicoSugerida: {}", dto.getDataUltimoServicoSugerida());
            }
        }
        
        if (dirty) {
            clienteRepo.save(cliente);
        }

        ClienteEndereco endereco = null;
        if (dto.getEnderecosSugeridos() != null && !dto.getEnderecosSugeridos().isEmpty()) {
            EnderecoSugeridoDTO sug = dto.getEnderecosSugeridos().get(0);
            List<ClienteEndereco> existentes = enderecoRepo.findByClienteId(cliente.getId());

            Optional<ClienteEndereco> match = existentes.stream()
                    .filter(e -> isAddressMatch(e, sug))
                    .findFirst();

            if (match.isPresent()) {
                endereco = match.get();
                if (endereco.getHorarioFuncionamento() == null && sug.getHorarioFuncionamento() != null) {
                     endereco.setHorarioFuncionamento(sug.getHorarioFuncionamento());
                     enderecoRepo.save(endereco);
                }
            } else {
                endereco = createEndereco(cliente, sug, existentes.isEmpty());
            }
        } else {
            endereco = enderecoRepo.findByClienteIdAndIsDefaultTrue(cliente.getId()).orElse(null);
        }

        op.setClienteRef(cliente);
        if (endereco != null) {
            op.setEndereco(endereco);
        }

        return endereco;
    }

    private Cliente createCliente(OpImportRequestDTO dto, String nomeNormalizado) {
        Cliente c = new Cliente();
        c.setNomeOficial(dto.getClienteNomeOficial().trim());
        c.setNomeNormalizado(nomeNormalizado);
        c.setAtivo(true);
        c.setOrigin("OP");
        c.setCnpjCpf(dto.getCnpjCpf());
        c.setInscricaoEstadual(dto.getInscricaoEstadual());
        c.setTelefone(dto.getTelefone());
        c.setEmailContato(dto.getEmailContato());
        
        // Defaults requested by user
        c.setPadraoEntrega(dto.getPadraoEntregaSugerido() != null ? dto.getPadraoEntregaSugerido() : "A ENTREGAR");
        c.setHorarioFuncionamento("08:00 - 18:00"); // Default commercial hours
        c.setTransportadora(null); // Default "Nenhuma"

        return clienteRepo.save(c);
    }

    private ClienteEndereco createEndereco(Cliente cliente, EnderecoSugeridoDTO sug, boolean isFirst) {
        ClienteEndereco e = new ClienteEndereco();
        e.setCliente(cliente);
        e.setCidade(sug.getCidade());
        e.setUf(sug.getUf());
        e.setBairro(sug.getBairro());
        e.setLogradouro(sug.getLogradouro());
        e.setCep(sug.getCep());
        e.setHorarioFuncionamento(sug.getHorarioFuncionamento());
        e.setPadraoEntrega(sug.getPadraoEntrega());
        e.setOrigin("OP");
        e.setConfidence("HIGH"); 
        e.setIsDefault(isFirst);
        return enderecoRepo.save(e);
    }

    private boolean isAddressMatch(ClienteEndereco db, EnderecoSugeridoDTO sug) {
        if (sug.getCidade() != null && db.getCidade() != null && !sug.getCidade().equalsIgnoreCase(db.getCidade())) return false;
        if (sug.getUf() != null && db.getUf() != null && !sug.getUf().equalsIgnoreCase(db.getUf())) return false;
        
        String dbLog = normalizar(db.getLogradouro());
        String sugLog = normalizar(sug.getLogradouro());
        
        if (dbLog.isEmpty() && sugLog.isEmpty()) return true;
        
        return dbLog.contains(sugLog) || sugLog.contains(dbLog);
    }

    private String normalizar(String s) {
        if (s == null) return "";
        return java.text.Normalizer
                .normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .trim();
    }
}