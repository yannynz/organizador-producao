package git.yannynz.organizadorproducao.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;

@Entity
@Table(name = "op_import", uniqueConstraints = {
        @UniqueConstraint(name = "uk_op_import_numero_op", columnNames = "numero_op")
})
public class OpImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_op", nullable = false, unique = true)
    private String numeroOp;

    @Column(name = "codigo_produto")
    private String codigoProduto;

    @Column(name = "descricao_produto")
    private String descricaoProduto;

    private String cliente;

    @Column(name = "data_op")
    private ZonedDateTime dataOp;

    @Column(name = "emborrachada", nullable = false)
    private boolean emborrachada;

    @Column(name = "share_path")
    private String sharePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode materiais;

    // NOVOS CAMPOS
    @Column(name = "destacador")
    private String destacador; // "M", "F", "MF", etc

    @Column(name = "modalidade_entrega")
    private String modalidadeEntrega; // "RETIRADA" ou "A ENTREGAR"

    @Column(name = "faca_id")
    private Long facaId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "data_requerida_entrega")
    private ZonedDateTime dataRequeridaEntrega;

    @Column(name = "usuario_importacao")
    private String usuarioImportacao;

    // Materiais especiais (podem ser nulos quando não informados)
    @Column(name = "pertinax")
    private Boolean pertinax;

    @Column(name = "poliester")
    private Boolean poliester;

    @Column(name = "papel_calibrado")
    private Boolean papelCalibrado;

    @Column(name = "vai_vinco")
    private Boolean vaiVinco;

    @Column(name = "manual_lock_emborrachada", nullable = false)
    private boolean manualLockEmborrachada;

    @Column(name = "manual_lock_pertinax", nullable = false)
    private boolean manualLockPertinax;

    @Column(name = "manual_lock_poliester", nullable = false)
    private boolean manualLockPoliester;

    @Column(name = "manual_lock_papel_calibrado", nullable = false)
    private boolean manualLockPapelCalibrado;

    @Column(name = "manual_lock_vai_vinco", nullable = false)
    private boolean manualLockVaiVinco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente clienteRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id")
    private ClienteEndereco endereco;

    public OpImport() {
    }

    public Cliente getClienteRef() {
        return clienteRef;
    }

    public void setClienteRef(Cliente clienteRef) {
        this.clienteRef = clienteRef;
    }

    public ClienteEndereco getEndereco() {
        return endereco;
    }

    public void setEndereco(ClienteEndereco endereco) {
        this.endereco = endereco;
    }

    // ---- getters/setters ----
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

    public Boolean getVaiVinco() {
        return vaiVinco;
    }

    public void setVaiVinco(Boolean vaiVinco) {
        this.vaiVinco = vaiVinco;
    }

    public boolean isManualLockEmborrachada() {
        return manualLockEmborrachada;
    }

    public void setManualLockEmborrachada(boolean manualLockEmborrachada) {
        this.manualLockEmborrachada = manualLockEmborrachada;
    }

    public boolean isManualLockPertinax() {
        return manualLockPertinax;
    }

    public void setManualLockPertinax(boolean manualLockPertinax) {
        this.manualLockPertinax = manualLockPertinax;
    }

    public boolean isManualLockPoliester() {
        return manualLockPoliester;
    }

    public void setManualLockPoliester(boolean manualLockPoliester) {
        this.manualLockPoliester = manualLockPoliester;
    }

    public boolean isManualLockPapelCalibrado() {
        return manualLockPapelCalibrado;
    }

    public void setManualLockPapelCalibrado(boolean manualLockPapelCalibrado) {
        this.manualLockPapelCalibrado = manualLockPapelCalibrado;
    }

    public boolean isManualLockVaiVinco() {
        return manualLockVaiVinco;
    }

    public void setManualLockVaiVinco(boolean manualLockVaiVinco) {
        this.manualLockVaiVinco = manualLockVaiVinco;
    }

    public ZonedDateTime getDataOp() {
        return dataOp;
    }

    public void setDataOp(ZonedDateTime dataOp) {
        this.dataOp = dataOp;
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

    public String getModalidadeEntrega() {
        return modalidadeEntrega;
    }

    public void setModalidadeEntrega(String modalidadeEntrega) {
        this.modalidadeEntrega = modalidadeEntrega;
    }

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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
