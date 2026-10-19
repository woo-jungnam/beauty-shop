package com.core.beautyshop.modules.notification.application.service;
import com.core.beautyshop.modules.notification.api.CustomerNotificationFacade;
import com.core.beautyshop.modules.notification.domain.CustomerNotification;
import com.core.beautyshop.shared.dto.PageResponse;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class CustomerNotificationService implements CustomerNotificationFacade {
    private final EntityManager em;
    @Override @Transactional public void publish(Long uid,String key,String type,String title,String body,String target){
        if(uid==null)return;
        em.createNativeQuery("select id from users where id=:uid for update").setParameter("uid",uid).getSingleResult();
        long count=em.createQuery("select count(n) from CustomerNotification n where n.userId=:uid and n.eventKey=:key",Long.class).setParameter("uid",uid).setParameter("key",key).getSingleResult();
        if(count>0)return;
        var n=new CustomerNotification();n.setUserId(uid);n.setEventKey(key);n.setType(type);n.setTitle(title);n.setBody(body);n.setTarget(target);n.setCreatedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));em.persist(n);
    }
    @Transactional(readOnly=true) public PageResponse<View> mine(int page,int size){
        if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE)throw new BusinessException("Phân trang không hợp lệ");
        Long uid=SecurityUtils.getCurrentUserId();
        long total=em.createQuery("select count(n) from CustomerNotification n where n.userId=:uid",Long.class).setParameter("uid",uid).getSingleResult();
        var rows=em.createQuery("select n from CustomerNotification n where n.userId=:uid order by n.id desc",CustomerNotification.class).setParameter("uid",uid).setFirstResult(page*size).setMaxResults(size).getResultList();
        return PageResponse.of(new PageImpl<>(rows.stream().map(this::view).toList(),PageRequest.of(page,size),total));
    }
    @Transactional public View read(Long id){
        var n=em.find(CustomerNotification.class,id);
        if(n==null || !n.getUserId().equals(SecurityUtils.getCurrentUserId()))throw new ResourceNotFoundException("Không tìm thấy thông báo của bạn");
        if(n.getReadAt()==null)n.setReadAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));return view(n);
    }
    private View view(CustomerNotification n){return new View(n.getId(),n.getType(),n.getTitle(),n.getBody(),n.getTarget(),n.getCreatedAt(),n.getReadAt());}
    public record View(Long id,String type,String title,String body,String target,Instant createdAt,Instant readAt){}
}
