package git.yannynz.organizadorproducao.model.dto;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderSearchDTO {

    // Busca livre (aplica em nr, cliente, observacao, entregador, veiculo, recebedor, montador, prioridade)
    private String q;

    // 1:1 com Order
    private String nr;
    private String cliente;
    private String prioridade;
    private Integer status;          
    private List<Integer> statusIn;

    private String entregador;
    private String observacao;
    private String veiculo;
    private String recebedor;
    private String montador;
    private String emborrachador;
    private Range dataH;
    private Range dataEntrega;
    private Range dataHRetorno;
    private Range dataMontagem;
    private ZonedDateTime dataEmborrachamento;

    public static class Range {
        private ZonedDateTime from;
        private ZonedDateTime to;
        public ZonedDateTime getFrom() { return from; }
        public void setFrom(ZonedDateTime from) { this.from = from; }
        public ZonedDateTime getTo() { return to; }
        public void setTo(ZonedDateTime to) { this.to = to; }
    }

    // getters/setters
    
    public String getEmborrachador() { return emborrachador; }
    public void setEmborrachador(String emborrachador) { this.emborrachador = emborrachador; }

    public ZonedDateTime getDataEmborrachamento() { return dataEmborrachamento; }
    public void setDataEmborrachamento(ZonedDateTime dataEmborrachamento) { this.dataEmborrachamento = dataEmborrachamento; }

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }

    public String getNr() { return nr; }
    public void setNr(String nr) { this.nr = nr; }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public String getPrioridade() { return prioridade; }
    public void setPrioridade(String prioridade) { this.prioridade = prioridade; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public List<Integer> getStatusIn() { return statusIn; }
    public void setStatusIn(List<Integer> statusIn) { this.statusIn = statusIn; }

    public String getEntregador() { return entregador; }
    public void setEntregador(String entregador) { this.entregador = entregador; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public String getVeiculo() { return veiculo; }
    public void setVeiculo(String veiculo) { this.veiculo = veiculo; }

    public String getRecebedor() { return recebedor; }
    public void setRecebedor(String recebedor) { this.recebedor = recebedor; }

    public String getMontador() { return montador; }
    public void setMontador(String montador) { this.montador = montador; }

    public Range getDataH() { return dataH; }
    public void setDataH(Range dataH) { this.dataH = dataH; }

    public Range getDataEntrega() { return dataEntrega; }
    public void setDataEntrega(Range dataEntrega) { this.dataEntrega = dataEntrega; }

    public Range getDataHRetorno() { return dataHRetorno; }
    public void setDataHRetorno(Range dataHRetorno) { this.dataHRetorno = dataHRetorno; }

    public Range getDataMontagem() { return dataMontagem; }
    public void setDataMontagem(Range dataMontagem) { this.dataMontagem = dataMontagem; }
}

