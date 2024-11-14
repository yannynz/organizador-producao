package git.yannynz.organizadorproducao.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nr;
    private String cliente;
    private String prioridade;
    private LocalDateTime dataH;
    private int status;
    private LocalDateTime dataEntrega;
    private String entregador;
    private String observacao;
    private String veiculo;  
    private LocalDateTime dataHRetorno;  

    public Order() {}

    public String getVeiculo() {
        return veiculo;
    }

    public void setVeiculo(String veiculo) {
        this.veiculo = veiculo;
    }

    public LocalDateTime getDataHRetorno() {
        return dataHRetorno;
    }

    public void setDataHRetorno(LocalDateTime dataHRetorno) {
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

    public LocalDateTime getDataH() {
        return dataH;
    }

    public void setDataH(LocalDateTime dataH) {
        this.dataH = dataH;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public LocalDateTime getDataEntrega() {
        return dataEntrega;
    }

    public void setDataEntrega(LocalDateTime dataEntrega) {
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
