package git.yannynz.organizadorproducao.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "clientes")
public class Cliente {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "nome_oficial", nullable = false, length = 180)
  private String nomeOficial;

  @Column(name = "nome_normalizado", nullable = false, length = 180, unique = true)
  private String nomeNormalizado;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private List<String> apelidos = new ArrayList<>();

  @Column(name = "padrao_entrega", length = 20)
  private String padraoEntrega; 

  @Column(name = "horario_funcionamento", length = 180)
  private String horarioFuncionamento;

  @Column(name = "cnpj_cpf", length = 20)
  private String cnpjCpf;

  @Column(name = "inscricao_estadual", length = 30)
  private String inscricaoEstadual;

  @Column(length = 60)
  private String telefone;

  @Column(name = "email_contato", length = 120)
  private String emailContato;

  private String observacoes;
  private Boolean ativo = true;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transportadora_id")
  private Transportadora transportadora;

  @Column(name = "ultimo_servico_em")
  private OffsetDateTime ultimoServicoEm;

  private String origin; 

  @Column(name = "manual_lock_mask")
  private Short manualLockMask;

  @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private java.util.List<ClienteEndereco> enderecos;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public java.util.List<ClienteEndereco> getEnderecos() { return enderecos; }
  public void setEnderecos(java.util.List<ClienteEndereco> enderecos) { this.enderecos = enderecos; }

  public String getNomeOficial() { return nomeOficial; }
  public void setNomeOficial(String nomeOficial) { this.nomeOficial = nomeOficial; }

  public String getNomeNormalizado() { return nomeNormalizado; }
  public void setNomeNormalizado(String nomeNormalizado) { this.nomeNormalizado = nomeNormalizado; }

  public List<String> getApelidos() { return apelidos; }
  public void setApelidos(List<String> apelidos) { this.apelidos = apelidos != null ? apelidos : new ArrayList<>(); }
  /** Accepts comma-separated strings when coming from legacy payloads. */
  public void setApelidos(String apelidosCsv) {
      if (apelidosCsv == null) {
          this.apelidos = new ArrayList<>();
          return;
      }
      String trimmed = apelidosCsv.trim();
      if (trimmed.isEmpty()) {
          this.apelidos = new ArrayList<>();
          return;
      }
      String[] parts = trimmed.split("\\s*,\\s*");
      this.apelidos = new ArrayList<>();
      for (String p : parts) {
          if (!p.isBlank()) this.apelidos.add(p.trim());
      }
  }

  public String getPadraoEntrega() { return padraoEntrega; }
  public void setPadraoEntrega(String padraoEntrega) { this.padraoEntrega = padraoEntrega; }

  public String getHorarioFuncionamento() { return horarioFuncionamento; }
  public void setHorarioFuncionamento(String horarioFuncionamento) { this.horarioFuncionamento = horarioFuncionamento; }

  public String getCnpjCpf() { return cnpjCpf; }
  public void setCnpjCpf(String cnpjCpf) { this.cnpjCpf = cnpjCpf; }

  public String getInscricaoEstadual() { return inscricaoEstadual; }
  public void setInscricaoEstadual(String inscricaoEstadual) { this.inscricaoEstadual = inscricaoEstadual; }

  public String getTelefone() { return telefone; }
  public void setTelefone(String telefone) { this.telefone = telefone; }

  public String getEmailContato() { return emailContato; }
  public void setEmailContato(String emailContato) { this.emailContato = emailContato; }

  public String getObservacoes() { return observacoes; }
  public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

  public Boolean getAtivo() { return ativo; }
  public void setAtivo(Boolean ativo) { this.ativo = ativo; }

  public Transportadora getTransportadora() { return transportadora; }
  public void setTransportadora(Transportadora transportadora) { this.transportadora = transportadora; }

  public OffsetDateTime getUltimoServicoEm() { return ultimoServicoEm; }
  public void setUltimoServicoEm(OffsetDateTime ultimoServicoEm) { this.ultimoServicoEm = ultimoServicoEm; }

  public String getOrigin() { return origin; }
  public void setOrigin(String origin) { this.origin = origin; }

  public Short getManualLockMask() { return manualLockMask; }
  public void setManualLockMask(Short manualLockMask) { this.manualLockMask = manualLockMask; }

  @Transient
  private Long transportadoraId;

  public Long getTransportadoraId() {
      return transportadora != null ? transportadora.getId() : transportadoraId;
  }

  public void setTransportadoraId(Long transportadoraId) {
      this.transportadoraId = transportadoraId;
  }

  @Transient
  public String getTransportadoraName() {
      return transportadora != null ? transportadora.getNomeOficial() : null;
  }
}
