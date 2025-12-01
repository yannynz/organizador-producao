package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.model.Transportadora;
import git.yannynz.organizadorproducao.service.TransportadoraService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transportadoras")
public class TransportadoraController {

    private final TransportadoraService service;

    public TransportadoraController(TransportadoraService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Transportadora> search(@RequestParam(required = false) String search, Pageable pageable) {
        return service.search(search, pageable);
    }

    @GetMapping("/{id}")
    public Transportadora getById(@PathVariable Long id) {
        return service.findById(id).orElseThrow(() -> new RuntimeException("Transportadora não encontrada"));
    }

    @PostMapping
    public Transportadora create(@RequestBody Transportadora transportadora) {
        return service.create(transportadora);
    }

    @PatchMapping("/{id}")
    public Transportadora update(@PathVariable Long id, @RequestBody Transportadora transportadora) {
        return service.update(id, transportadora);
    }
}
