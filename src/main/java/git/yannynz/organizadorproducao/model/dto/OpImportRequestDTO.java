package git.yannynz.organizadorproducao.model.dto;

import java.util.List;

public class OpImportRequestDTO {
  private String numeroOp;
  private String codigoProduto;
  private String descricaoProduto;
  private String cliente;
  private String dataOp;           // yyyy-MM-dd
  private List<String> materiais;
  private Boolean emborrachada;
  private String sharePath;

  public OpImportRequestDTO() {}

  // getters/setters
  public String getNumeroOp() { return numeroOp; }
  public void setNumeroOp(String numeroOp) { this.numeroOp = numeroOp; }
  public String getCodigoProduto() { return codigoProduto; }
  public void setCodigoProduto(String codigoProduto) { this.codigoProduto = codigoProduto; }
  public String getDescricaoProduto() { return descricaoProduto; }
  public void setDescricaoProduto(String descricaoProduto) { this.descricaoProduto = descricaoProduto; }
  public String getCliente() { return cliente; }
  public void setCliente(String cliente) { this.cliente = cliente; }
  public String getDataOp() { return dataOp; }
  public void setDataOp(String dataOp) { this.dataOp = dataOp; }
  public java.util.List<String> getMateriais() { return materiais; }
  public void setMateriais(java.util.List<String> materiais) { this.materiais = materiais; }
  public Boolean getEmborrachada() { return emborrachada; }
  public void setEmborrachada(Boolean emborrachada) { this.emborrachada = emborrachada; }
  public String getSharePath() { return sharePath; }
  public void setSharePath(String sharePath) { this.sharePath = sharePath; }
}

