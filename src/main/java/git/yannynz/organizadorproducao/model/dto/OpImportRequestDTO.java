package git.yannynz.organizadorproducao.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpImportRequestDTO {
    private String numeroOp;
    private String codigoProduto;
    private String descricaoProduto;
    private String cliente;
    private String dataOp; // yyyy-MM-dd
    private List<String> materiais;
    private Boolean emborrachada;
    private Boolean vaiVinco;
    private String sharePath;

    private String destacador;
    private String modalidadeEntrega;
    private String dataRequeridaEntrega; // "yyyy-MM-dd"
    private String usuarioImportacao;

    private Boolean pertinax;
    private Boolean poliester;
    private Boolean papelCalibrado;

    private String clienteNomeOficial;
    private String cnpjCpf;
    private String inscricaoEstadual;
    private String telefone;
    private String emailContato;
    private java.util.List<String> apelidosSugeridos;
    private java.util.List<EnderecoSugeridoDTO> enderecosSugeridos;
    private String padraoEntregaSugerido; // "RETIRA" | "A_ENTREGAR"
    private String dataUltimoServicoSugerida; // ISO ou yyyy-MM-dd'T'HH:mm

    public OpImportRequestDTO() {
    }

    // getters/setters
    public String getClienteNomeOficial() {
        return clienteNomeOficial;
    }

    public void setClienteNomeOficial(String clienteNomeOficial) {
        this.clienteNomeOficial = clienteNomeOficial;
    }

    public String getCnpjCpf() { return cnpjCpf; }
    public void setCnpjCpf(String cnpjCpf) { this.cnpjCpf = cnpjCpf; }

    public String getInscricaoEstadual() { return inscricaoEstadual; }
    public void setInscricaoEstadual(String inscricaoEstadual) { this.inscricaoEstadual = inscricaoEstadual; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getEmailContato() { return emailContato; }
    public void setEmailContato(String emailContato) { this.emailContato = emailContato; }

    public java.util.List<String> getApelidosSugeridos() {
        return apelidosSugeridos;
    }

    public void setApelidosSugeridos(java.util.List<String> apelidosSugeridos) {
        this.apelidosSugeridos = apelidosSugeridos;
    }

    public java.util.List<EnderecoSugeridoDTO> getEnderecosSugeridos() {
        return enderecosSugeridos;
    }

    public void setEnderecosSugeridos(java.util.List<EnderecoSugeridoDTO> enderecosSugeridos) {
        this.enderecosSugeridos = enderecosSugeridos;
    }

    public String getPadraoEntregaSugerido() {
        return padraoEntregaSugerido;
    }

    public void setPadraoEntregaSugerido(String padraoEntregaSugerido) {
        this.padraoEntregaSugerido = padraoEntregaSugerido;
    }

    public String getDataUltimoServicoSugerida() {
        return dataUltimoServicoSugerida;
    }

    public void setDataUltimoServicoSugerida(String dataUltimoServicoSugerida) {
        this.dataUltimoServicoSugerida = dataUltimoServicoSugerida;
    }

    public String getUsuarioImportacao() {
        return usuarioImportacao;
    }

    public void setUsuarioImportacao(String usuarioImportacao) {
        this.usuarioImportacao = usuarioImportacao;
    }

    public Boolean getPertinax() {
        return pertinax;
    }

    public void setPertinax(Boolean pertinax) {
        this.pertinax = pertinax;
    }

    public Boolean getPoliester() {
        return poliester;
    }

    public void setPoliester(Boolean poliester) {
        this.poliester = poliester;
    }

    public Boolean getPapelCalibrado() {
        return papelCalibrado;
    }

    public void setPapelCalibrado(Boolean papelCalibrado) {
        this.papelCalibrado = papelCalibrado;
    }

    public String getDataRequeridaEntrega() {
        return dataRequeridaEntrega;
    }

    public void setDataRequeridaEntrega(String dataRequeridaEntrega) {
        this.dataRequeridaEntrega = dataRequeridaEntrega;
    }

    public String getDestacador() {
        return destacador;
    }

    public void setDestacador(String destacador) {
        this.destacador = destacador;
    }

    public String getModalidadeEntrega() {
        return modalidadeEntrega;
    }

    public void setModalidadeEntrega(String modalidadeEntrega) {
        this.modalidadeEntrega = modalidadeEntrega;
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

    public String getDataOp() {
        return dataOp;
    }

    public void setDataOp(String dataOp) {
        this.dataOp = dataOp;
    }

    public java.util.List<String> getMateriais() {
        return materiais;
    }

    public void setMateriais(java.util.List<String> materiais) {
        this.materiais = materiais;
    }

    public Boolean getEmborrachada() {
        return emborrachada;
    }

    public void setEmborrachada(Boolean emborrachada) {
        this.emborrachada = emborrachada;
    }

    public Boolean getVaiVinco() {
        return vaiVinco;
    }

    public void setVaiVinco(Boolean vaiVinco) {
        this.vaiVinco = vaiVinco;
    }

    public String getSharePath() {
        return sharePath;
    }

    public void setSharePath(String sharePath) {
        this.sharePath = sharePath;
    }
}
