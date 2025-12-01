package git.yannynz.organizadorproducao.model;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
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
    private ZonedDateTime dataCortada;
    private ZonedDateTime dataTirada;
    private String destacador;
    private String modalidadeEntrega;
    private ZonedDateTime dataRequeridaEntrega;
    private String usuarioImportacao;
    private boolean pertinax;
    private boolean poliester;
    private boolean papelCalibrado;

    @Column(name = "vai_vinco")
    private boolean vaiVinco;

    @Column(name = "vincador")
    private String vincador;

    @Column(name = "data_vinco")
    private ZonedDateTime dataVinco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente clienteRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transportadora_id")
    private Transportadora transportadora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id")
    private ClienteEndereco endereco;

    @Column(name = "horario_func_aplicado")
    private String horarioFuncAplicado;

    @Column(name = "fora_horario")
    private Boolean foraHorario;

    public Order() {
    }

    public String getVincador() {
        return vincador;
    }

    public void setVincador(String vincador) {
        this.vincador = vincador;
    }

    public ZonedDateTime getDataVinco() {
        return dataVinco;
    }

    public void setDataVinco(ZonedDateTime dataVinco) {
        this.dataVinco = dataVinco;
    }

    public String getModalidadeEntrega() {
        return modalidadeEntrega;
    }

    public void setModalidadeEntrega(String modalidadeEntrega) {
        this.modalidadeEntrega = modalidadeEntrega;
    }

    public String getUsuarioImportacao() {
        return usuarioImportacao;
    }

    public void setUsuarioImportacao(String usuarioImportacao) {
        this.usuarioImportacao = usuarioImportacao;
    }

    public boolean isPertinax() {
        return pertinax;
    }

    public void setPertinax(boolean pertinax) {
        this.pertinax = pertinax;
    }

    public boolean isPoliester() {
        return poliester;
    }

    public void setPoliester(boolean poliester) {
        this.poliester = poliester;
    }

    public boolean isPapelCalibrado() {
        return papelCalibrado;
    }

    public void setPapelCalibrado(boolean papelCalibrado) {
        this.papelCalibrado = papelCalibrado;
    }

    public boolean isVaiVinco() {
        return vaiVinco;
    }

    public void setVaiVinco(boolean vaiVinco) {
        this.vaiVinco = vaiVinco;
    }

    public ZonedDateTime getDataRequeridaEntrega() {
        return dataRequeridaEntrega;
    }

    public void setDataRequeridaEntrega(ZonedDateTime dataRequeridaEntrega) {
        this.dataRequeridaEntrega = dataRequeridaEntrega;
    }

    public String getDestacador() {
        return destacador;
    }

    public void setDestacador(String destacador) {
        this.destacador = destacador;
    }

    public ZonedDateTime getDataCortada() {
        return dataCortada;
    }

    public void setDataCortada(ZonedDateTime dataCortada) {
        this.dataCortada = dataCortada;
    }

    public ZonedDateTime getDataTirada() {
        return dataTirada;
    }

    public void setDataTirada(ZonedDateTime dataTirada) {
        this.dataTirada = dataTirada;
    }

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

    public Cliente getClienteRef() {
        return clienteRef;
    }

    public void setClienteRef(Cliente clienteRef) {
        this.clienteRef = clienteRef;
    }

    public Transportadora getTransportadora() {
        return transportadora;
    }

    public void setTransportadora(Transportadora transportadora) {
        this.transportadora = transportadora;
    }

    public ClienteEndereco getEndereco() {
        return endereco;
    }

    public void setEndereco(ClienteEndereco endereco) {
        this.endereco = endereco;
    }

    public String getHorarioFuncAplicado() {
        return horarioFuncAplicado;
    }

    public void setHorarioFuncAplicado(String horarioFuncAplicado) {
        this.horarioFuncAplicado = horarioFuncAplicado;
    }

    public Boolean getForaHorario() {
        return foraHorario;
    }

    public void setForaHorario(Boolean foraHorario) {
        this.foraHorario = foraHorario;
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
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Order order = (Order) o;

        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
