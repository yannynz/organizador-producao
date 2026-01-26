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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
                    .sorted(Comparator.comparing(e -> !Boolean.TRUE.equals(e.getIsDefault())))
                    .findFirst();

            if (match.isPresent()) {
                endereco = match.get();
                if (applySuggestedAddress(endereco, sug)) {
                    enderecoRepo.save(endereco);
                }
            } else {
                endereco = createEndereco(cliente, sug, existentes.isEmpty());
            }

            if (endereco != null) {
                maybePromoteDefault(existentes, endereco);
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
        if (db == null || sug == null) return false;

        String dbCep = normalizeCep(db.getCep());
        String sugCep = normalizeCep(sug.getCep());
        String dbLog = normalizar(db.getLogradouro());
        String sugLog = normalizar(sug.getLogradouro());

        // If CEP + logradouro (and UF when present) match, treat as same address even if bairro/cidade differ.
        if (!dbCep.isEmpty() && !sugCep.isEmpty() && dbCep.equals(sugCep) && !dbLog.isEmpty() && !sugLog.isEmpty()) {
            if (dbLog.contains(sugLog) || sugLog.contains(dbLog)) {
                String dbUf = safeUpper(db.getUf());
                String sugUf = safeUpper(sug.getUf());
                if (dbUf.isEmpty() || sugUf.isEmpty() || dbUf.equals(sugUf)) {
                    return true;
                }
            }
        }

        if (sug.getCidade() != null && db.getCidade() != null && !sug.getCidade().equalsIgnoreCase(db.getCidade())) return false;
        if (sug.getUf() != null && db.getUf() != null && !sug.getUf().equalsIgnoreCase(db.getUf())) return false;

        if (dbLog.isEmpty() && sugLog.isEmpty()) return true;
        
        return dbLog.contains(sugLog) || sugLog.contains(dbLog);
    }

    private String normalizar(String s) {
        if (s == null) return "";
        String normalized = java.text.Normalizer
                .normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .trim();
        
        // Expansão de abreviações comuns para melhorar o match
        return normalized
                .replaceAll("\\bAV\\.?\\b", "AVENIDA")
                .replaceAll("\\bR\\.?\\b", "RUA")
                .replaceAll("\\bDR\\.?\\b", "DOUTOR")
                .replaceAll("\\bDRA\\.?\\b", "DOUTORA")
                .replaceAll("\\bPRACA\\.?\\b", "PRACA") // Add PRACA as it's common
                .replaceAll("\\bTRAV\\.?\\b", "TRAVESSA")
                .replaceAll("\\bESTR\\.?\\b", "ESTRADA")
                .replaceAll("\\bROD\\.?\\b", "RODOVIA")
                .replaceAll("\\bAL\\.?\\b", "ALAMEDA")
                .replaceAll("\\bBLV\\.?\\b", "BOULEVARD")
                .replaceAll("\\bCAM\\.?\\b", "CAMINHO")
                .replaceAll("\\bESC\\.?\\b", "ESCADARIA")
                .replaceAll("\\bLD\\.?\\b", "LADEIRA")
                .replaceAll("\\bPARK\\.?\\b", "PARQUE")
                .replaceAll("\\bPC\\.?\\b", "PRACA")
                .replaceAll("\\bQD\\.?\\b", "QUADRA")
                .replaceAll("\\bSQN\\.?\\b", "SUPERQUADRA NORTE")
                .replaceAll("\\bSQS\\.?\\b", "SUPERQUADRA SUL")
                .replaceAll("\\bTV\\.?\\b", "TRAVESSA")
                .replaceAll("\\bVD\\.?\\b", "VIADUTO")
                .replaceAll("\\bVL\\.?\\b", "VILA")
                .replaceAll("\\bZS\\.?\\b", "ZONA SUL")
                .replaceAll("\\bZN\\.?\\b", "ZONA NORTE")
                .replaceAll("\\bZL\\.?\\b", "ZONA LESTE")
                .replaceAll("\\bZO\\.?\\b", "ZONA OESTE")
                .replaceAll("\\bKM\\.?\\b", "KILOMETRO")
                .replaceAll("\\bN\\.?\\b", "NORTE")
                .replaceAll("\\bS\\.?\\b", "SUL")
                .replaceAll("\\bL\\.?\\b", "LESTE")
                .replaceAll("\\bO\\.?\\b", "OESTE")
                .replaceAll("\\bNUM\\.?\\b", "NUMERO")
                .replaceAll("\\bNRO\\.?\\b", "NUMERO")
                .replaceAll("\\bCOMPL\\.?\\b", "COMPLEMENTO")
                .replaceAll("\\bAPTO\\.?\\b", "APARTAMENTO")
                .replaceAll("\\bCJ\\.?\\b", "CONJUNTO")
                .replaceAll("\\bBL\\.?\\b", "BLOCO")
                .replaceAll("\\bLT\\.?\\b", "LOTE")
                .replaceAll("\\bQDRA\\.?\\b", "QUADRA")
                .replaceAll("\\bLOT\\.?\\b", "LOTEAMENTO")
                .replaceAll("\\bCONJ\\.?\\b", "CONJUNTO")
                .replaceAll("\\bEDF\\.?\\b", "EDIFICIO")
                .replaceAll("\\bANDAR\\.?\\b", "ANDAR")
                .replaceAll("\\bSL\\.?\\b", "SALA")
                .replaceAll("\\bGAL\\.?\\b", "GALERIA")
                .replaceAll("\\bLJ\\.?\\b", "LOJA")
                .replaceAll("\\bMOD\\.?\\b", "MODULO")
                .replaceAll("\\bPISO\\.?\\b", "PISO")
                .replaceAll("\\bSOBR\\.?\\b", "SOBRADO")
                .replaceAll("\\bTERR\\.?\\b", "TERREO")
                .replaceAll("\\bUNID\\.?\\b", "UNIDADE")
                .replaceAll("\\bZS\\.?\\b", "ZONA SUL")
                .replaceAll("\\bZN\\.?\\b", "ZONA NORTE")
                .replaceAll("\\bZL\\.?\\b", "ZONA LESTE")
                .replaceAll("\\bZO\\.?\\b", "ZONA OESTE")
                .replaceAll("\\bKM\\.?\\b", "KILOMETRO")
                .replaceAll("\\bN\\.?\\b", "NORTE")
                .replaceAll("\\bS\\.?\\b", "SUL")
                .replaceAll("\\bL\\.?\\b", "LESTE")
                .replaceAll("\\bO\\.?\\b", "OESTE")
                .replaceAll("\\bJD\\.?\\b", "JARDIM")
                .replaceAll("\\bPQ\\.?\\b", "PARQUE")
                .replaceAll("\\bRES\\.?\\b", "RESIDENCIAL")
                .replaceAll("\\bSTA\\.?\\b", "SANTA")
                .replaceAll("\\bSTO\\.?\\b", "SANTO")
                .replaceAll("\\bSANTANA\\.?\\b", "SANTANA")
                .replaceAll("\\bNS\\.?\\b", "NOSSA SENHORA")
                .replaceAll("\\bENGENHO\\.?\\b", "ENGENHO")
                .replaceAll("\\bFAZ\\.?\\b", "FAZENDA")
                .replaceAll("\\bCHAC\\.?\\b", "CHACARA")
                .replaceAll("\\bSITIO\\.?\\b", "SITIO")
                .replaceAll("\\bGRJ\\.?\\b", "GRANJA")
                .replaceAll("\\bCOL\\.?\\b", "COLONIA")
                .replaceAll("\\bCOND\\.?\\b", "CONDOMINIO")
                .replaceAll("\\bPRAIA\\.?\\b", "PRAIA")
                .replaceAll("\\bCID\\.?\\b", "CIDADE")
                .replaceAll("\\bVL\\.?\\b", "VILA")
                .replaceAll("\\bCT\\.?\\b", "CENTRO")
                .replaceAll("\\bCENTRO\\.?\\b", "CENTRO")
                .replaceAll("\\bCOM\\.?\\b", "COMERCIAL")
                .replaceAll("\\bIND\\.?\\b", "INDUSTRIAL")
                .replaceAll("\\bUNIV\\.?\\b", "UNIVERSITARIO")
                .replaceAll("\\bCOOP\\.?\\b", "COOPERATIVA")
                .replaceAll("\\bASS\\.?\\b", "ASSOCIACAO")
                .replaceAll("\\bSOC\\.?\\b", "SOCIEDADE")
                .replaceAll("\\bINST\\.?\\b", "INSTITUTO")
                .replaceAll("\\bCOL\\.?\\b", "COLEGIO")
                .replaceAll("\\bESC\\.?\\b", "ESCOLA")
                .replaceAll("\\bFAC\\.?\\b", "FACULDADE")
                .replaceAll("\\bUNIV\\.?\\b", "UNIVERSIDADE")
                .replaceAll("\\bHOSP\\.?\\b", "HOSPITAL")
                .replaceAll("\\bCLIN\\.?\\b", "CLINICA")
                .replaceAll("\\bLAB\\.?\\b", "LABORATORIO")
                .replaceAll("\\bFARM\\.?\\b", "FARMACIA")
                .replaceAll("\\bCONS\\.?\\b", "CONSULTORIO")
                .replaceAll("\\bPONT\\.?\\b", "PONTO")
                .replaceAll("\\bREF\\.?\\b", "REFERENCIA")
                .replaceAll("\\bPROX\\.?\\b", "PROXIMO")
                .replaceAll("\\bESQ\\.?\\b", "ESQUINA")
                .replaceAll("\\bFRENTE\\.?\\b", "FRENTE")
                .replaceAll("\\bFUNDOS\\.?\\b", "FUNDOS")
                .replaceAll("\\bDIR\\.?\\b", "DIREITA")
                .replaceAll("\\bESQ\\.?\\b", "ESQUERDA")
                .replaceAll("\\bMARG\\.?\\b", "MARGEM")
                .replaceAll("\\bMARG ESQ\\.?\\b", "MARGEM ESQUERDA")
                .replaceAll("\\bMARG DIR\\.?\\b", "MARGEM DIREITA")
                .replaceAll("\\bALTOS\\.?\\b", "ALTOS")
                .replaceAll("\\bBAIXOS\\.?\\b", "BAIXOS")
                .replaceAll("\\bSUBSOL\\.?\\b", "SUBSOLO")
                .replaceAll("\\bTERR\\.?\\b", "TERREO")
                .replaceAll("\\bFUNDOS\\.?\\b", "FUNDOS")
                .replaceAll("\\bFRENTE\\.?\\b", "FRENTE")
                .replaceAll("\\bPORTARIA\\.?\\b", "PORTARIA")
                .replaceAll("\\bBLOCO\\.?\\b", "BLOCO")
                .replaceAll("\\bCASA\\.?\\b", "CASA")
                .replaceAll("\\bAPTO\\.?\\b", "APARTAMENTO")
                .replaceAll("\\bKM\\.?\\b", "KILOMETRO")
                .replaceAll("\\bS/N\\.?\\b", "SEM NUMERO")
                .replaceAll("\\bSN\\.?\\b", "SEM NUMERO")
                .replaceAll("\\bLOT\\.?\\b", "LOTE")
                .replaceAll("\\bQD\\.?\\b", "QUADRA")
                .replaceAll("\\bLT\\.?\\b", "LOTE")
                .replaceAll("\\bGLP\\.?\\b", "GALPAO")
                .replaceAll("\\bGALPAO\\.?\\b", "GALPAO")
                .replaceAll("\\bMOD\\.?\\b", "MODULO")
                .replaceAll("\\bPVS\\.?\\b", "PAVILHAO")
                .replaceAll("\\bPAV\\.?\\b", "PAVILHAO")
                .replaceAll("\\bENTR\\.?\\b", "ENTRADA")
                .replaceAll("\\bSAIDA\\.?\\b", "SAIDA")
                .replaceAll("\\bSETOR\\.?\\b", "SETOR")
                .replaceAll("\\bAREA\\.?\\b", "AREA")
                .replaceAll("\\bPERIM\\.?\\b", "PERIMETRO")
                .replaceAll("\\bCOL\\.?\\b", "COLONIA")
                .replaceAll("\\bCOM\\.?\\b", "COMERCIAL")
                .replaceAll("\\bRES\\.?\\b", "RESIDENCIAL")
                .replaceAll("\\bIND\\.?\\b", "INDUSTRIAL")
                .replaceAll("\\bRUR\\.?\\b", "RURAL")
                .replaceAll("\\bURB\\.?\\b", "URBANA")
                .replaceAll("\\bZUR\\.?\\b", "ZONA URBANA")
                .replaceAll("\\bZRU\\.?\\b", "ZONA RURAL")
                .replaceAll("\\bEST\\.?\\b", "ESTANCIA")
                .replaceAll("\\bFAZ\\.?\\b", "FAZENDA")
                .replaceAll("\\bGRANJA\\.?\\b", "GRANJA")
                .replaceAll("\\bSITIO\\.?\\b", "SITIO")
                .replaceAll("\\bCHACARA\\.?\\b", "CHACARA")
                .replaceAll("\\bAGRO\\.?\\b", "AGROPECUARIA")
                .replaceAll("\\bNUC\\.?\\b", "NUCLEO")
                .replaceAll("\\bCOL\\.?\\b", "COLONIA")
                .replaceAll("\\bDISTR\\.?\\b", "DISTRITO")
                .replaceAll("\\bBAIRRO\\.?\\b", "BAIRRO")
                .replaceAll("\\bCENTRO\\.?\\b", "CENTRO")
                .replaceAll("\\bSETOR\\.?\\b", "SETOR")
                .replaceAll("\\bRESID\\.?\\b", "RESIDENCIAL")
                .replaceAll("\\bJARD\\.?\\b", "JARDIM")
                .replaceAll("\\bPARQ\\.?\\b", "PARQUE")
                .replaceAll("\\bURB\\.?\\b", "URBANO")
                .replaceAll("\\bRUR\\.?\\b", "RURAL")
                .replaceAll("\\bZONA\\.?\\b", "ZONA")
                .replaceAll("\\bSET\\.?\\b", "SETOR")
                .replaceAll("\\bUNIV\\.?\\b", "UNIVERSITARIO")
                .replaceAll("\\bPOLO\\.?\\b", "POLO")
                .replaceAll("\\bDIST\\.?\\b", "DISTRITO")
                .replaceAll("\\bCOMER\\.?\\b", "COMERCIAL")
                .replaceAll("\\bINDUS\\.?\\b", "INDUSTRIAL")
                .replaceAll("\\bEMP\\.?\\b", "EMPRESARIAL")
                .replaceAll("\\bCONJ\\.?\\b", "CONJUNTO")
                .replaceAll("\\bESCAD\\.?\\b", "ESCADARIA")
                .replaceAll("\\bPASSA\\.?\\b", "PASSAGEM")
                .replaceAll("\\bBECO\\.?\\b", "BECO")
                .replaceAll("\\bVIELA\\.?\\b", "VIELA")
                .replaceAll("\\bRUA\\.?\\b", "RUA")
                .replaceAll("\\bAVENIDA\\.?\\b", "AVENIDA")
                .replaceAll("\\bTRV\\.?\\b", "TRAVESSA")
                .replaceAll("\\bPRACA\\.?\\b", "PRACA")
                .replaceAll("\\bESTRADA\\.?\\b", "ESTRADA")
                .replaceAll("\\bRODOVIA\\.?\\b", "RODOVIA")
                .replaceAll("\\bALAMEDA\\.?\\b", "ALAMEDA")
                .replaceAll("\\BOULEVARD\\.?\\b", "BOULEVARD")
                .replaceAll("\\bCAMINHO\\.?\\b", "CAMINHO")
                .replaceAll("\\bLADEIRA\\.?\\b", "LADEIRA")
                .replaceAll("\\bPARQUE\\.?\\b", "PARQUE")
                .replaceAll("\\bQUADRA\\.?\\b", "QUADRA")
                .replaceAll("\\bSUPERQUADRA NORTE\\.?\\b", "SUPERQUADRA NORTE")
                .replaceAll("\\bSUPERQUADRA SUL\\.?\\b", "SUPERQUADRA SUL")
                .replaceAll("\\bVIADUTO\\.?\\b", "VIADUTO")
                .replaceAll("\\bVILA\\.?\\b", "VILA");
    }

    private boolean applySuggestedAddress(ClienteEndereco endereco, EnderecoSugeridoDTO sug) {
        if (endereco == null || sug == null) return false;
        if (Boolean.TRUE.equals(endereco.getManualLock())) return false;

        boolean dirty = false;

        dirty |= applyFieldIfUseful(endereco::getCep, endereco::setCep, sug.getCep());
        dirty |= applyFieldIfUseful(endereco::getLogradouro, endereco::setLogradouro, sug.getLogradouro());
        dirty |= applyFieldIfUseful(endereco::getUf, endereco::setUf, sug.getUf());
        dirty |= applyFieldIfUseful(endereco::getPadraoEntrega, endereco::setPadraoEntrega, sug.getPadraoEntrega());
        dirty |= applyFieldIfUseful(endereco::getHorarioFuncionamento, endereco::setHorarioFuncionamento, sug.getHorarioFuncionamento());

        dirty |= applyCidadeBairro(endereco, sug);

        return dirty;
    }

    private boolean applyCidadeBairro(ClienteEndereco endereco, EnderecoSugeridoDTO sug) {
        String cidade = safeTrim(endereco.getCidade());
        String bairro = safeTrim(endereco.getBairro());
        String sugCidade = safeTrim(sug.getCidade());
        String sugBairro = safeTrim(sug.getBairro());

        boolean dirty = false;

        if (!sugCidade.isEmpty() && shouldReplaceCidade(cidade, sugCidade, sugBairro)) {
            endereco.setCidade(sugCidade);
            dirty = true;
        } else if (cidade.isEmpty() && !sugCidade.isEmpty()) {
            endereco.setCidade(sugCidade);
            dirty = true;
        }

        if (!sugBairro.isEmpty() && shouldReplaceBairro(bairro, sugBairro, sugCidade)) {
            endereco.setBairro(sugBairro);
            dirty = true;
        } else if (bairro.isEmpty() && !sugBairro.isEmpty()) {
            endereco.setBairro(sugBairro);
            dirty = true;
        }

        return dirty;
    }

    private boolean shouldReplaceCidade(String existing, String suggested, String suggestedBairro) {
        if (suggested.isEmpty()) return false;
        if (existing.isEmpty()) return true;
        if (equalsIgnoreCase(existing, suggested)) return false;

        var exTokens = tokens(existing);
        var sugTokens = tokens(suggested);

        if (exTokens.size() == 1 && sugTokens.size() > 1 && endsWithIgnoreCase(suggested, existing)) {
            return true;
        }

        if (!suggestedBairro.isEmpty() && tokenOverlap(tokens(suggestedBairro), exTokens)) {
            return true;
        }

        if (sugTokens.size() > exTokens.size() && containsIgnoreCase(suggested, existing)) {
            return true;
        }

        return false;
    }

    private boolean shouldReplaceBairro(String existing, String suggested, String suggestedCidade) {
        if (suggested.isEmpty()) return false;
        if (existing.isEmpty()) return true;
        if (equalsIgnoreCase(existing, suggested)) return false;

        var exTokens = tokens(existing);
        var sugTokens = tokens(suggested);

        if (!suggestedCidade.isEmpty() && tokenOverlap(tokens(suggestedCidade), exTokens)) {
            return true;
        }

        if (sugTokens.size() > exTokens.size() && containsIgnoreCase(suggested, existing)) {
            return true;
        }

        return false;
    }

    private void maybePromoteDefault(List<ClienteEndereco> existentes, ClienteEndereco candidato) {
        if (candidato == null || existentes == null) return;

        ClienteEndereco currentDefault = existentes.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsDefault()))
                .findFirst()
                .orElse(null);

        if (currentDefault == null) {
            setDefault(existentes, candidato);
            return;
        }

        if (currentDefault.getId() != null && currentDefault.getId().equals(candidato.getId())) {
            return;
        }

        if (Boolean.TRUE.equals(currentDefault.getManualLock())) {
            return;
        }

        if (scoreEndereco(candidato) > scoreEndereco(currentDefault)) {
            setDefault(existentes, candidato);
        }
    }

    private void setDefault(List<ClienteEndereco> existentes, ClienteEndereco novoDefault) {
        for (ClienteEndereco e : existentes) {
            boolean shouldBeDefault = e.getId() != null && e.getId().equals(novoDefault.getId());
            if (!Boolean.valueOf(shouldBeDefault).equals(e.getIsDefault())) {
                e.setIsDefault(shouldBeDefault);
                enderecoRepo.save(e);
            }
        }
    }

    private int scoreEndereco(ClienteEndereco e) {
        int score = 0;
        if (e == null) return score;
        if (!safeTrim(e.getCep()).isEmpty()) score += 3;
        if (!safeTrim(e.getLogradouro()).isEmpty()) score += 3;
        if (!safeTrim(e.getCidade()).isEmpty()) score += 2;
        if (!safeTrim(e.getBairro()).isEmpty()) score += 2;
        if (!safeTrim(e.getUf()).isEmpty()) score += 1;
        if (!safeTrim(e.getPadraoEntrega()).isEmpty()) score += 1;
        if (!safeTrim(e.getHorarioFuncionamento()).isEmpty()) score += 1;
        if ("HIGH".equalsIgnoreCase(safeTrim(e.getConfidence()))) score += 1;
        return score;
    }

    private boolean applyFieldIfUseful(Supplier<String> getter, Consumer<String> setter, String suggested) {
        String current = safeTrim(getter.get());
        String sug = safeTrim(suggested);
        if (sug.isEmpty()) return false;
        if (current.isEmpty()) {
            setter.accept(sug);
            return true;
        }
        if (!equalsIgnoreCase(current, sug) && containsIgnoreCase(sug, current)) {
            setter.accept(sug);
            return true;
        }
        return false;
    }

    private String normalizeCep(String cep) {
        if (cep == null) return "";
        return cep.replaceAll("\\D", "");
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private String safeUpper(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return safeTrim(a).equalsIgnoreCase(safeTrim(b));
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return safeTrim(haystack).toUpperCase().contains(safeTrim(needle).toUpperCase());
    }

    private boolean endsWithIgnoreCase(String haystack, String needle) {
        return safeTrim(haystack).toUpperCase().endsWith(safeTrim(needle).toUpperCase());
    }

    private List<String> tokens(String value) {
        if (value == null) return List.of();
        return Arrays.stream(value.trim().split("\\s+"))
                .filter(t -> !t.isBlank())
                .map(String::toUpperCase)
                .toList();
    }

    private boolean tokenOverlap(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        for (String token : a) {
            if (b.contains(token)) return true;
        }
        return false;
    }
}
