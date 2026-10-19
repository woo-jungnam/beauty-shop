package com.core.beautyshop.modules.crm.application;
import com.core.beautyshop.modules.crm.domain.CrmWorkItem;
import com.core.beautyshop.modules.crm.domain.CustomerCareNote;
import com.core.beautyshop.modules.identity.api.IdentityFacade;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class CrmWorkService {
    private final EntityManager em;
    private final IdentityFacade identity;
    private final JdbcTemplate jdbc;
    private static final Set<String> STATES=Set.of("OPEN","IN_PROGRESS","DONE","CANCELLED");
    @Transactional public void fromNote(CustomerCareNote note){
        if(note.getFollowUpAt()==null && !"COMPLAINT".equals(note.getNoteType()))return;
        if(em.createQuery("select count(w) from CrmWorkItem w where w.sourceNoteId=:id",Long.class).setParameter("id",note.getId()).getSingleResult()>0)return;
        var w=new CrmWorkItem();w.setCustomerId(note.getUserId());w.setSourceNoteId(note.getId());
        boolean complaint="COMPLAINT".equals(note.getNoteType());w.setKind(complaint?"COMPLAINT":"FOLLOW_UP");
        w.setTitle(complaint?"Xử lý khiếu nại":"Liên hệ chăm sóc khách hàng");w.setDescription(note.getContent());w.setDueAt(note.getFollowUpAt());initialize(w);em.persist(w);
    }
    @Transactional public View create(Create c){
        identity.getUserSummaryById(c.customerId());validateAssignee(c.assigneeId());
        if(!Set.of("FOLLOW_UP","COMPLAINT").contains(c.kind()))throw new BusinessException("Loại công việc không hợp lệ");
        var w=new CrmWorkItem();w.setCustomerId(c.customerId());w.setKind(c.kind());w.setTitle(c.title().trim());w.setDescription(c.description());w.setAssigneeId(c.assigneeId());w.setDueAt(c.dueAt());initialize(w);em.persist(w);em.flush();return view(w);
    }
    @Transactional(readOnly=true) public PageResponse<View> list(String status,String kind,Instant dueBefore,Long assignee,Long customer,int page,int size){
        if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw new BusinessException("Phân trang không hợp lệ");
        if(status!=null&&!STATES.contains(status))throw new BusinessException("Trạng thái không hợp lệ");
        if(kind!=null&&!Set.of("FOLLOW_UP","COMPLAINT").contains(kind))throw new BusinessException("Loại không hợp lệ");
        String where=" where 1=1";Map<String,Object> params=new HashMap<>();
        if(status!=null){where+=" and w.status=:status";params.put("status",status);}
        if(kind!=null){where+=" and w.kind=:kind";params.put("kind",kind);}
        if(dueBefore!=null){where+=" and w.dueAt<=:due and w.status in ('OPEN','IN_PROGRESS')";params.put("due",dueBefore);}
        if(assignee!=null){where+=" and w.assigneeId=:assignee";params.put("assignee",assignee);}
        if(customer!=null){where+=" and w.customerId=:customer";params.put("customer",customer);}
        var count=em.createQuery("select count(w) from CrmWorkItem w"+where,Long.class);
        var query=em.createQuery("select w from CrmWorkItem w"+where+" order by case when w.dueAt is null then 1 else 0 end,w.dueAt,w.id",CrmWorkItem.class);
        params.forEach((k,v)->{count.setParameter(k,v);query.setParameter(k,v);});
        var rows=query.setFirstResult(page*size).setMaxResults(size).getResultList().stream().map(this::view).toList();
        return PageResponse.of(new PageImpl<>(rows,PageRequest.of(page,size),count.getSingleResult()));
    }
    @Transactional public View update(Long id,Update c){
        var w=em.find(CrmWorkItem.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(w==null)throw new ResourceNotFoundException("Không tìm thấy công việc");
        if(w.getVersion()!=c.expectedVersion())throw new BusinessException("Công việc đã thay đổi, vui lòng tải lại");
        if(!STATES.contains(c.status()))throw new BusinessException("Trạng thái không hợp lệ");
        if(Set.of("DONE","CANCELLED").contains(c.status())&&(c.outcome()==null||c.outcome().isBlank()))throw new BusinessException("Cần ghi kết quả hoặc lý do đóng công việc");
        validateAssignee(c.assigneeId());
        w.setAssigneeId(c.assigneeId());w.setDueAt(c.dueAt());w.setStatus(c.status());w.setOutcome(c.outcome());w.setUpdatedAt(Instant.now());
        w.setCompletedAt(Set.of("DONE","CANCELLED").contains(c.status())?Instant.now():null);em.flush();return view(w);
    }
    private void validateAssignee(Long id){
        if(id==null)return;
        Integer count=jdbc.queryForObject("select count(*) from users u where u.id=? and u.is_deleted=false and u.status='ACTIVE' and (exists(select 1 from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=u.id and r.name='ROLE_ADMIN' and r.is_deleted=false) or exists(select 1 from staff_permissions p where p.user_id=u.id and p.permission='CRM'))",Integer.class,id);
        if(count==null||count==0)throw new BusinessException("Người phụ trách phải có quyền chăm sóc khách hàng");
        if(!SecurityUtils.isAdmin()&&!Objects.equals(id,SecurityUtils.getCurrentUserId()))throw new BusinessException("Nhân viên chỉ được tự nhận công việc; quản trị viên phân công người khác");
    }
    private void initialize(CrmWorkItem w){w.setStatus("OPEN");w.setCreatedAt(Instant.now());w.setUpdatedAt(w.getCreatedAt());}
    private View view(CrmWorkItem w){var customer=identity.findUserSummaryById(w.getCustomerId()).orElse(null);var assignee=w.getAssigneeId()==null?null:identity.findUserSummaryById(w.getAssigneeId()).orElse(null);return new View(w.getId(),w.getCustomerId(),customer==null?null:customer.getFullName(),w.getKind(),w.getTitle(),w.getDescription(),w.getAssigneeId(),assignee==null?null:assignee.getFullName(),w.getDueAt(),w.getStatus(),w.getOutcome(),w.getCreatedAt(),w.getUpdatedAt(),w.getCompletedAt(),w.getVersion(),w.getDueAt()!=null&&w.getDueAt().isBefore(Instant.now())&&Set.of("OPEN","IN_PROGRESS").contains(w.getStatus()));}
    public record Create(@NotNull Long customerId,@NotBlank String kind,@NotBlank @Size(max=200) String title,@Size(max=10000) String description,Long assigneeId,Instant dueAt){}
    public record Update(@NotBlank String status,Long assigneeId,Instant dueAt,@Size(max=2000) String outcome,long expectedVersion){}
    public record View(Long id,Long customerId,String customerName,String kind,String title,String description,Long assigneeId,String assigneeName,Instant dueAt,String status,String outcome,Instant createdAt,Instant updatedAt,Instant completedAt,long version,boolean overdue){}
}
