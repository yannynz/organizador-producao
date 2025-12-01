package git.yannynz.organizadorproducao.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cliente_enderecos")
public class ClienteEndereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Cliente cliente;

    private String label;
    private String uf;
    private String cidade;
    private String bairro;
    private String logradouro;
    
    @Column(length = 10)
    private String cep;

    @Column(name = "horario_funcionamento")
    private String horarioFuncionamento;

    @Column(name = "padrao_entrega")
    private String padraoEntrega;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    private String origin;
    private String confidence;
    
    @Column(name = "manual_lock")
    private Boolean manualLock = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }

    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }

    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }

    public String getHorarioFuncionamento() { return horarioFuncionamento; }
    public void setHorarioFuncionamento(String horarioFuncionamento) { this.horarioFuncionamento = horarioFuncionamento; }

    public String getPadraoEntrega() { return padraoEntrega; }
    public void setPadraoEntrega(String padraoEntrega) { this.padraoEntrega = padraoEntrega; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public Boolean getManualLock() { return manualLock; }
    public void setManualLock(Boolean manualLock) { this.manualLock = manualLock; }
}