package git.yannynz.organizadorproducao.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transportadoras")
public class Transportadora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_oficial", nullable = false, length = 180)
    private String nomeOficial;

    @Column(name = "nome_normalizado", nullable = false, length = 180)
    private String nomeNormalizado;

    @Column(columnDefinition = "text")
    private String apelidos;

    private String localizacao;

    @Column(name = "horario_funcionamento")
    private String horarioFuncionamento;

    @Column(name = "ultimo_servico_em")
    private OffsetDateTime ultimoServicoEm;

    @Column(name = "padrao_entrega")
    private String padraoEntrega;

    private String observacoes;
    private Boolean ativo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNomeOficial() { return nomeOficial; }
    public void setNomeOficial(String nomeOficial) { this.nomeOficial = nomeOficial; }

    public String getNomeNormalizado() { return nomeNormalizado; }
    public void setNomeNormalizado(String nomeNormalizado) { this.nomeNormalizado = nomeNormalizado; }

    public String getApelidos() { return apelidos; }
    public void setApelidos(String apelidos) { this.apelidos = apelidos; }

    public String getLocalizacao() { return localizacao; }
    public void setLocalizacao(String localizacao) { this.localizacao = localizacao; }

    public String getHorarioFuncionamento() { return horarioFuncionamento; }
    public void setHorarioFuncionamento(String horarioFuncionamento) { this.horarioFuncionamento = horarioFuncionamento; }

    public OffsetDateTime getUltimoServicoEm() { return ultimoServicoEm; }
    public void setUltimoServicoEm(OffsetDateTime ultimoServicoEm) { this.ultimoServicoEm = ultimoServicoEm; }

    public String getPadraoEntrega() { return padraoEntrega; }
    public void setPadraoEntrega(String padraoEntrega) { this.padraoEntrega = padraoEntrega; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}