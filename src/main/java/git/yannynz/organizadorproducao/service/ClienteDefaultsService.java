package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.Cliente;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.repository.ClienteRepository;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ClienteDefaultsService {

    private final ClienteRepository clienteRepository;

    public ClienteDefaultsService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    public boolean applyDefaults(Order order) {
        if (order == null) {
            return false;
        }
        Cliente cliente = order.getClienteRef();
        if (cliente == null && hasText(order.getCliente())) {
            cliente = findByNome(order.getCliente()).orElse(null);
            if (cliente != null) {
                order.setClienteRef(cliente);
            }
        }
        return applyDefaults(order, cliente);
    }

    public boolean applyDefaults(Order order, Cliente cliente) {
        if (order == null || cliente == null) {
            return false;
        }
        boolean changed = false;

        if (Boolean.TRUE.equals(cliente.getDefaultEmborrachada()) && !order.isEmborrachada()) {
            order.setEmborrachada(true);
            changed = true;
        }
        if (Boolean.TRUE.equals(cliente.getDefaultPertinax()) && !order.isPertinax()) {
            order.setPertinax(true);
            changed = true;
        }
        if (Boolean.TRUE.equals(cliente.getDefaultPoliester()) && !order.isPoliester()) {
            order.setPoliester(true);
            changed = true;
        }
        if (Boolean.TRUE.equals(cliente.getDefaultPapelCalibrado()) && !order.isPapelCalibrado()) {
            order.setPapelCalibrado(true);
            changed = true;
        }

        String defaultDestacador = normalizeDestacador(cliente.getDefaultDestacador());
        if (hasText(defaultDestacador) && !Objects.equals(order.getDestacador(), defaultDestacador)) {
            order.setDestacador(defaultDestacador);
            changed = true;
        }

        return changed;
    }

    private Optional<Cliente> findByNome(String nome) {
        String normalized = normalize(nome);
        if (!hasText(normalized)) {
            return Optional.empty();
        }
        return clienteRepository.findByNomeNormalizadoOrApelido(normalized);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .trim();
    }

    private String normalizeDestacador(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        if ("M/F".equals(trimmed) || "F/M".equals(trimmed)) {
            return "MF";
        }
        return trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
