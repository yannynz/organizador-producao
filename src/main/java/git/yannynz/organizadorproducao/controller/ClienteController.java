package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.model.Cliente;
import git.yannynz.organizadorproducao.service.ClienteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Cliente> search(@RequestParam(required = false) String search, Pageable pageable) {
        return service.search(search, pageable);
    }

    @GetMapping("/{id}")
    public Cliente getById(@PathVariable Long id) {
        return service.findById(id).orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
    }

    @PostMapping
    public Cliente create(@RequestBody Cliente cliente) {
        return service.create(cliente);
    }

    @PatchMapping("/{id}")
    public Cliente update(@PathVariable Long id, @RequestBody Cliente cliente) {
        return service.update(id, cliente);
    }
}
