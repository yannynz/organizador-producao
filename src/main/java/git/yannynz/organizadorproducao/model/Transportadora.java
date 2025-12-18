package git.yannynz.organizadorproducao.model;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> apelidos = new ArrayList<>();

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

    public List<String> getApelidos() { return apelidos; }
    public void setApelidos(List<String> apelidos) { this.apelidos = apelidos != null ? apelidos : new ArrayList<>(); }
    /** Accepts comma-separated strings from legacy payloads. */
    public void setApelidos(String apelidosCsv) {
        if (apelidosCsv == null) {
            this.apelidos = new ArrayList<>();
            return;
        }
        String trimmed = apelidosCsv.trim();
        if (trimmed.isEmpty()) {
            this.apelidos = new ArrayList<>();
            return;
        }
        String[] parts = trimmed.split("\\s*,\\s*");
        this.apelidos = new ArrayList<>();
        for (String p : parts) {
            if (!p.isBlank()) this.apelidos.add(p.trim());
        }
    }

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
