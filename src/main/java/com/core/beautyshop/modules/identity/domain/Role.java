package com.core.beautyshop.modules.identity.domain;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Table;

@Table(name="roles")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends Base {
    @Column(name = "name", length = 50, unique = true, nullable = false)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    public String getRoleName() {
        return this.name;
    }

    public void setRoleName(String roleName) {
        this.name = roleName;
    }

    public static class RoleBuilder {
        public RoleBuilder roleName(String roleName) {
            this.name = roleName;
            return this;
        }
    }
}

