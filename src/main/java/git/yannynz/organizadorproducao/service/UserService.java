package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.domain.user.User;
import git.yannynz.organizadorproducao.domain.user.UserRepository;
import git.yannynz.organizadorproducao.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public List<User> findAll() {
        return repository.findAll();
    }

    public Optional<User> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    public User create(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return repository.save(user);
    }

    public List<User> findAssignableUsers(UserRole requesterRole) {
        EnumSet<UserRole> roles = EnumSet.of(UserRole.OPERADOR, UserRole.ADMIN);
        if (requesterRole == UserRole.ADMIN || requesterRole == UserRole.DESENHISTA) {
            roles.add(UserRole.DESENHISTA);
        }
        return repository.findByActiveTrueAndRoleIn(roles)
                .stream()
                .sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public User update(Long id, User userDetails) {
        return repository.findById(id)
                .map(user -> {
                    user.setName(userDetails.getName());
                    user.setEmail(userDetails.getEmail());
                    user.setRole(userDetails.getRole());
                    user.setActive(userDetails.isActive());
                    if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
                        user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
                    }
                    return repository.save(user);
                })
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
