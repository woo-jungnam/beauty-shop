package com.core.beautyshop.modules.inventory.domain;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="inventory_commands") @Getter @Setter @NoArgsConstructor
public class InventoryCommand extends Base{
 @Column(name="command_key",nullable=false,unique=true,length=100)private String commandKey;
 @Column(name="command_hash",nullable=false,length=64)private String commandHash;
 @Column(name="result_json",nullable=false,columnDefinition="TEXT")private String resultJson;
}
