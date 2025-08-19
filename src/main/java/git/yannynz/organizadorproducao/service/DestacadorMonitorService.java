package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class DestacadorMonitorService {

    /* Servico dedicado a registrar eventos de destaque de facas e monitorar seus status  */

    private enum Sexo { M, F, DESCONHECIDO }

    private static final ZoneId TZ_SP = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FMT = DateTimeFormatter
            .ofPattern("dd/MM HH:mm")
            .withLocale(new Locale("pt","BR"));

    // detecta tokens separados por _ ou - (sem confundir com VERMELHO etc.)
    private static final Pattern MACHO = Pattern.compile("(?i)(?:^|[_-])MACHO(?:[_-]|\\.|$)");
    private static final Pattern FEMEA = Pattern.compile("(?i)(?:^|[_-])F[ÊE]MEA(?:[_-]|\\.|$)");

    private static final Pattern NR_OR_CL = Pattern.compile("(?i)(?:NR|CL)(\\d+)");

    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public DestacadorMonitorService(OrderRepository orderRepository,
                                    SimpMessagingTemplate messagingTemplate) {
        this.orderRepository = orderRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public void registrarAguardandoCorte(String fileName) {
        anexarLinha(fileName, "a cortar");
    }

    @Transactional
    public void registrarCortado(String fileName) {
        anexarLinha(fileName, "cortado");
    }

    /* ======= privados ======= */

   private void anexarLinha(String fileName, String etapa) {
    Optional<String> nrOpt = extractOrderNumber(fileName);
    if (nrOpt.isEmpty()) return;

    Sexo sexo = detectSexo(fileName);
    if (sexo == Sexo.DESCONHECIDO) return; // não registra genérico

    orderRepository.findByNr(nrOpt.get()).ifPresent(order -> {
        String obs = Optional.ofNullable(order.getObservacao()).orElse("");

        final String LM  = "• Destaque M: "   + etapa;
        final String LF  = "• Destaque F: "   + etapa;
        final String LMF = "• Destaque M/F: " + etapa;

        boolean hasM  = containsLine(obs, LM);
        boolean hasF  = containsLine(obs, LF);
        boolean hasMF = containsLine(obs, LMF);

        if (!hasMF) {
            switch (sexo) {
                case M -> {
                    if (hasF) { // F + chegou M → colapsa em M/F
                        obs = removeLine(obs, LF);
                        obs = removeLine(obs, LM);
                        obs = appendLine(obs, LMF);
                    } else if (!hasM) {
                        obs = appendLine(obs, LM);
                    }
                }
                case F -> {
                    if (hasM) { // M + chegou F → colapsa em M/F
                        obs = removeLine(obs, LM);
                        obs = removeLine(obs, LF);
                        obs = appendLine(obs, LMF);
                    } else if (!hasF) {
                        obs = appendLine(obs, LF);
                    }
                }
                default -> { /* no-op */ }
            }
        }

        // 👉 Se a etapa atual é “cortado...”, e já consolidamos M/F para essa etapa,
        // remova qualquer linha “a cortar” que ainda sobrou (M, F ou M/F)
        if (isCortado(etapa) && containsLine(obs, "• Destaque M/F: cortado")) {
            obs = removeLine(obs, "• Destaque M/F: a cortar");
            obs = removeLine(obs, "• Destaque M: a cortar");
            obs = removeLine(obs, "• Destaque F: a cortar");
        }

        // limpeza leve de linhas em branco repetidas
        obs = obs.replaceAll("(?m)^[ \\t]+$", "");

        order.setObservacao(obs.trim());
        orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/orders", order);
    });
}

/* ===== helpers ===== */

private boolean isCortado(String etapa) {
    String e = etapa == null ? "" : etapa.toLowerCase(java.util.Locale.ROOT);
    return e.startsWith("cortad"); // casa "cortado" e "cortado (FACAS OK)"
}

private boolean containsLine(String obs, String line) {
    if (obs == null || obs.isBlank()) return false;
    return obs.lines().anyMatch(l -> l.trim().equals(line)); // compara por linha
}

private String removeLine(String obs, String line) {
    if (obs == null || obs.isBlank()) return "";
    String pattern = "(?m)^\\s*" + java.util.regex.Pattern.quote(line) + "\\s*$\\R?";
    return obs.replaceAll(pattern, ""); // replaceAll usa regex Java
}

private String appendLine(String obs, String line) {
    if (obs == null || obs.isBlank()) return line;
    return obs.endsWith(System.lineSeparator()) ? obs + line : obs + System.lineSeparator() + line;
}

    private Optional<String> extractOrderNumber(String fileName) {
        if (fileName == null) return Optional.empty();
        var m = NR_OR_CL.matcher(fileName);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private Sexo detectSexo(String fileName) {
        if (fileName == null) return Sexo.DESCONHECIDO;
        String base = fileName; // nome completo já funciona com os padrões acima
        if (MACHO.matcher(base).find()) return Sexo.M;
        if (FEMEA.matcher(base).find()) return Sexo.F;
        return Sexo.DESCONHECIDO;
    }
}

