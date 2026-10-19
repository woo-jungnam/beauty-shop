package com.core.beautyshop.modules.inventory.domain;
import com.core.beautyshop.shared.domain.Base;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="recall_outreach",uniqueConstraints=@UniqueConstraint(columnNames={"recall_id","subject_type","subject_id"})) @Getter @Setter @NoArgsConstructor
public class RecallOutreach extends Base {
 @Column(name="recall_id",nullable=false)private Long recallId;
 @Column(name="subject_type",nullable=false,length=20)private String subjectType;
 @Column(name="subject_id",nullable=false)private Long subjectId;
 @Column(nullable=false,length=20)private String status;
 @Column(length=1000)private String outcome;
 @Column(name="actor_id",nullable=false)private Long actorId;
}
