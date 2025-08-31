package git.yannynz.organizadorproducao.model;

import java.util.Objects;
import jakarta.persistence.*;
import java.time.ZonedDateTime;


@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nr;
    private String cliente;
    private String prioridade;
    private ZonedDateTime dataH;
    private int status;
    private ZonedDateTime dataEntrega;
    private String entregador;
    private String observacao;
    private String veiculo;  
    private ZonedDateTime dataHRetorno;  
    private String recebedor;
    private String montador;
    private ZonedDateTime dataMontagem;
    private String emborrachador;
    private ZonedDateTime dataEmborrachamento;
    private boolean emborrachada;

    public Order() {}


    public boolean isEmborrachada() {
        return emborrachada;
    }

    public void setEmborrachada(boolean emborrachada) {
        this.emborrachada = emborrachada;
    }

    public String getEmborrachador() {
        return emborrachador;
    }

    public void setEmborrachador(String emborrachador) {
        this.emborrachador = emborrachador;
    }

    public ZonedDateTime getDataEmborrachamento() {
        return dataEmborrachamento;
    }

    public void setDataEmborrachamento(ZonedDateTime dataEmborrachamento) {
        this.dataEmborrachamento = dataEmborrachamento;
    }

    public String getRecebedor() {
        return recebedor;
    }

    public void setRecebedor(String recebedor) {
        this.recebedor = recebedor;
    }

    public String getVeiculo() {
        return veiculo;
    }

    public void setVeiculo(String veiculo) {
        this.veiculo = veiculo;
    }

    public ZonedDateTime getDataHRetorno() {
        return dataHRetorno;
    }

    public void setDataHRetorno(ZonedDateTime dataHRetorno) {
        this.dataHRetorno = dataHRetorno;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNr() {
        return nr;
    }

    public void setNr(String nr) {
        this.nr = nr;
    }

    public String getCliente() {
        return cliente;
    }

    public void setCliente(String cliente) {
        this.cliente = cliente;
    }

    public String getPrioridade() {
        return prioridade;
    }

    public void setPrioridade(String prioridade) {
        this.prioridade = prioridade;
    }

    public ZonedDateTime getDataH() {
        return dataH;
    }

    public void setDataH(ZonedDateTime dataH) {
        this.dataH = dataH;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public ZonedDateTime getDataEntrega() {
        return dataEntrega;
    }

    public void setDataEntrega(ZonedDateTime dataEntrega) {
        this.dataEntrega = dataEntrega;
    }

    public String getEntregador() {
        return entregador;
    }

    public void setEntregador(String entregador) {
        this.entregador = entregador;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
    
    public String getMontador() {
        return montador;
    }

    public void setMontador(String montador) {
        this.montador = montador;
    }

    public ZonedDateTime getDataMontagem() {
        return dataMontagem;
    }
    
    public void setDataMontagem(ZonedDateTime dataMontagem) {
        this.dataMontagem = dataMontagem;
    }

  @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", nr='" + nr + '\'' +
                ", cliente='" + cliente + '\'' +
                ", prioridade='" + prioridade + '\'' +
                ", dataH=" + dataH +
                ", status=" + status +
                ", dataEntrega=" + dataEntrega +
                ", veiculo='" + veiculo + '\'' +
                ", dataHRetorno=" + dataHRetorno +
                ", recebedor='" + recebedor + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Order order = (Order) o;

        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
