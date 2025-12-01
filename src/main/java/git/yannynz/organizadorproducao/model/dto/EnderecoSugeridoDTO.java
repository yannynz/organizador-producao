package git.yannynz.organizadorproducao.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EnderecoSugeridoDTO {
  private String uf;
  private String cidade;
  private String bairro;
  private String logradouro;
  private String cep;
  private String horarioFuncionamento;
  private String padraoEntrega;

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
  public void setHorarioFuncionamento(String h) { this.horarioFuncionamento = h; }

  public String getPadraoEntrega() { return padraoEntrega; }
  public void setPadraoEntrega(String p) { this.padraoEntrega = p; }
}

