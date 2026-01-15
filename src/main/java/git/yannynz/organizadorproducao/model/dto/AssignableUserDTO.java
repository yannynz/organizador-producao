package git.yannynz.organizadorproducao.model.dto;

import git.yannynz.organizadorproducao.domain.user.UserRole;

public class AssignableUserDTO {
    private Long id;
    private String name;
    private UserRole role;

    public AssignableUserDTO() {
    }

    public AssignableUserDTO(Long id, String name, UserRole role) {
        this.id = id;
        this.name = name;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
