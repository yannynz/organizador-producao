package git.yannynz.organizadorproducao.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
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

  @Column(name = "default_emborrachada", nullable = false)
  private Boolean defaultEmborrachada = false;

  @Column(name = "default_destacador", length = 10)
  private String defaultDestacador;

  @Column(name = "default_pertinax", nullable = false)
  private Boolean defaultPertinax = false;

  @Column(name = "default_poliester", nullable = false)
  private Boolean defaultPoliester = false;

  @Column(name = "default_papel_calibrado", nullable = false)
  private Boolean defaultPapelCalibrado = false;

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

  @com.fasterxml.jackson.annotation.JsonProperty("apelidos")
  public List<String> getApelidos() { return apelidos; }
  @JsonIgnore
  public void setApelidos(List<String> apelidos) { this.apelidos = apelidos != null ? apelidos : new ArrayList<>(); }
  @JsonIgnore
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
  @JsonSetter("apelidos")
  public void setApelidosJson(Object value) {
      if (value == null) {
          this.apelidos = new ArrayList<>();
          return;
      }
      if (value instanceof java.util.List<?> list) {
          ArrayList<String> parsed = new ArrayList<>();
          for (Object item : list) {
              if (item == null) continue;
              String s = item.toString().trim();
              if (!s.isEmpty()) parsed.add(s);
          }
          this.apelidos = parsed;
          return;
      }
      if (value instanceof String s) {
          setApelidos(s);
          return;
      }
      this.apelidos = new ArrayList<>();
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

  public Boolean getDefaultEmborrachada() { return defaultEmborrachada; }
  public void setDefaultEmborrachada(Boolean defaultEmborrachada) { this.defaultEmborrachada = defaultEmborrachada; }

  public String getDefaultDestacador() { return defaultDestacador; }
  public void setDefaultDestacador(String defaultDestacador) { this.defaultDestacador = defaultDestacador; }

  public Boolean getDefaultPertinax() { return defaultPertinax; }
  public void setDefaultPertinax(Boolean defaultPertinax) { this.defaultPertinax = defaultPertinax; }

  public Boolean getDefaultPoliester() { return defaultPoliester; }
  public void setDefaultPoliester(Boolean defaultPoliester) { this.defaultPoliester = defaultPoliester; }

  public Boolean getDefaultPapelCalibrado() { return defaultPapelCalibrado; }
  public void setDefaultPapelCalibrado(Boolean defaultPapelCalibrado) { this.defaultPapelCalibrado = defaultPapelCalibrado; }

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
