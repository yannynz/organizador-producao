package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.domain.user.User;
import git.yannynz.organizadorproducao.domain.user.UserRole;
import git.yannynz.organizadorproducao.model.dto.AssignableUserDTO;
import git.yannynz.organizadorproducao.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @GetMapping("/assignable")
    public ResponseEntity<List<AssignableUserDTO>> getAssignable(Authentication auth) {
        UserRole requesterRole = Optional.ofNullable(auth)
                .map(Authentication::getPrincipal)
                .filter(User.class::isInstance)
                .map(User.class::cast)
                .map(User::getRole)
                .orElse(null);

        List<AssignableUserDTO> users = service.findAssignableUsers(requesterRole)
                .stream()
                .map(user -> new AssignableUserDTO(user.getId(), user.getName(), user.getRole()))
                .toList();

        return ResponseEntity.ok(users);
    }

    @GetMapping
    public ResponseEntity<List<User>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/me")
    public ResponseEntity<User> getMe() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return ResponseEntity.status(401).build();
        return service.findByEmail(auth.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody User user) {
        try {
            return ResponseEntity.ok(service.create(user));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body("Email já cadastrado.");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> update(@PathVariable Long id, @RequestBody User user) {
        return ResponseEntity.ok(service.update(id, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
