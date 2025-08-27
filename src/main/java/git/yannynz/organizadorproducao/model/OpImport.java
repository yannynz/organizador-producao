package git.yannynz.organizadorproducao.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "op_import")
public class OpImport {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "numero_op", unique = true, nullable = false)
  private String numeroOp;

  private String codigoProduto;

  @Column(columnDefinition = "text")
  private String descricaoProduto;

  @Column(columnDefinition = "text")
  private String cliente;

  private java.time.LocalDate dataOp;

  @Column(nullable = false)
  private boolean emborrachada;

  @Column(name = "share_path", nullable = false, columnDefinition = "text")
  private String sharePath;

  @JdbcTypeCode(SqlTypes.JSON)              // mapeia JSON -> jsonb no Postgres
  @Column(columnDefinition = "jsonb")
  private JsonNode materiais;

  private Long facaId;

  @Column(name = "created_at", insertable = false, updatable = false)
  private java.time.OffsetDateTime createdAt;

  // Getters e Setters

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getNumeroOp() {
    return numeroOp;
  }

  public void setNumeroOp(String numeroOp) {
    this.numeroOp = numeroOp;
  }

  public String getCodigoProduto() {
    return codigoProduto;
  }

  public void setCodigoProduto(String codigoProduto) {
    this.codigoProduto = codigoProduto;
  }

  public String getDescricaoProduto() {
    return descricaoProduto;
  }

  public void setDescricaoProduto(String descricaoProduto) {
    this.descricaoProduto = descricaoProduto;
  }

  public String getCliente() {
    return cliente;
  }

  public void setCliente(String cliente) {
    this.cliente = cliente;
  }

  public java.time.LocalDate getDataOp() {
    return dataOp;
  }

  public void setDataOp(java.time.LocalDate dataOp) {
    this.dataOp = dataOp;
  }

  public boolean isEmborrachada() {
    return emborrachada;
  }

  public void setEmborrachada(boolean emborrachada) {
    this.emborrachada = emborrachada;
  }

  public String getSharePath() {
    return sharePath;
  }

  public void setSharePath(String sharePath) {
    this.sharePath = sharePath;
  }

  public JsonNode getMateriais() {
    return materiais;
  }

  public void setMateriais(JsonNode materiais) {
    this.materiais = materiais;
  }

  public Long getFacaId() {
    return facaId;
  }

  public void setFacaId(Long facaId) {
    this.facaId = facaId;
  }

  public java.time.OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}

