package git.yannynz.organizadorproducao.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;  // <<<
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "op_import", uniqueConstraints = {
  @UniqueConstraint(name = "uk_op_import_numero_op", columnNames = "numero_op")
})
public class OpImport {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "numero_op", nullable = false, unique = true)
  private String numeroOp;

  @Column(name = "codigo_produto")
  private String codigoProduto;

  @Column(name = "descricao_produto")
  private String descricaoProduto;

  private String cliente;

  @Column(name = "data_op")
  private LocalDate dataOp;

  @Column(name = "emborrachada", nullable = false)
  private boolean emborrachada;

  @Column(name = "share_path")
  private String sharePath;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private JsonNode materiais;

  @Column(name = "faca_id")
  private Long facaId;

  @CreationTimestamp                                     // <<< preenche no INSERT
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  public OpImport() {}

  // ---- getters/setters ----
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getNumeroOp() { return numeroOp; }
  public void setNumeroOp(String numeroOp) { this.numeroOp = numeroOp; }

  public String getCodigoProduto() { return codigoProduto; }
  public void setCodigoProduto(String codigoProduto) { this.codigoProduto = codigoProduto; }

  public String getDescricaoProduto() { return descricaoProduto; }
  public void setDescricaoProduto(String descricaoProduto) { this.descricaoProduto = descricaoProduto; }

  public String getCliente() { return cliente; }
  public void setCliente(String cliente) { this.cliente = cliente; }

  public LocalDate getDataOp() { return dataOp; }
  public void setDataOp(LocalDate dataOp) { this.dataOp = dataOp; }

  public boolean isEmborrachada() { return emborrachada; }
  public void setEmborrachada(boolean emborrachada) { this.emborrachada = emborrachada; }

  public String getSharePath() { return sharePath; }
  public void setSharePath(String sharePath) { this.sharePath = sharePath; }

  public JsonNode getMateriais() { return materiais; }
  public void setMateriais(JsonNode materiais) { this.materiais = materiais; }

  public Long getFacaId() { return facaId; }
  public void setFacaId(Long facaId) { this.facaId = facaId; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

