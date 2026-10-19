package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.modules.identity.domain.CustomerAccountData.*;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.modules.identity.domain.enums.AccountStatus;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class CustomerAccountService {
    private final UserRepository users;
    private final EntityManager em;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final Optional<JavaMailSender> mail;
    private final JdbcTemplate jdbc;
    @Value("${app.mail.enabled:false}") private boolean mailEnabled;
    @Value("${app.mail.from:no-reply@beautyshop.vn}") private String from;
    @Value("${app.customer-url:http://localhost}") private String customerUrl;

    @Transactional(readOnly=true)
    public SkinView skin() {
        var p=em.find(SkinProfile.class, SecurityUtils.getCurrentUserId());
        return p==null ? new SkinView(null,null,null,null,null,null,null,true) : skinView(p);
    }
    @Transactional public SkinView updateSkin(SkinCommand c) {
        Long uid=lockCurrent().getId();
        var p=em.find(SkinProfile.class,uid);
        boolean created=p==null;
        if(created){ p=new SkinProfile(); p.setUserId(uid); }
        p.setSkinType(clean(c.skinType())); p.setSensitivity(clean(c.sensitivity()));
        p.setAllergies(clean(c.allergies())); p.setContraindications(clean(c.contraindications()));
        p.setPreferredIngredients(clean(c.preferredIngredients())); p.setNotes(clean(c.notes())); p.setUpdatedAt(Instant.now());
        if(created)em.persist(p);
        return skinView(p);
    }
    private SkinView skinView(SkinProfile p){return new SkinView(p.getSkinType(),p.getSensitivity(),p.getAllergies(),p.getContraindications(),p.getPreferredIngredients(),p.getNotes(),p.getUpdatedAt(),true);}

    @Transactional(readOnly=true) public List<AddressView> addresses(){
        return ownedAddresses(SecurityUtils.getCurrentUserId()).stream().map(this::addressView).toList();
    }
    @Transactional public AddressView saveAddress(Long id, AddressCommand c){
        Long uid=lockCurrent().getId(); var all=ownedAddresses(uid);
        Address a;
        if(id==null){
            if(all.size()>=20)throw new BusinessException("Chỉ lưu tối đa 20 địa chỉ");
            a=new Address(); a.setUserId(uid);
        } else {a=all.stream().filter(v->v.getId().equals(id)).findFirst().orElseThrow(()->new ResourceNotFoundException("Không tìm thấy địa chỉ của bạn"));}
        a.setRecipientName(c.recipientName().trim()); a.setPhone(c.phone().trim()); a.setAddress(c.address().trim());
        a.setWard(c.ward().trim()); a.setDistrict(c.district()==null?"":c.district().trim()); a.setCity(c.city().trim());
        boolean makeDefault=c.isDefault() || all.isEmpty();
        if(makeDefault)all.forEach(v->v.setDefaultAddress(false));
        a.setDefaultAddress(makeDefault || a.isDefaultAddress()); a.setUpdatedAt(Instant.now());
        if(id==null)em.persist(a);
        em.flush(); return addressView(a);
    }
    @Transactional public void deleteAddress(Long id){
        Long uid=lockCurrent().getId(); var all=ownedAddresses(uid);
        var a=all.stream().filter(v->v.getId().equals(id)).findFirst().orElseThrow(()->new ResourceNotFoundException("Không tìm thấy địa chỉ của bạn"));
        boolean wasDefault=a.isDefaultAddress(); em.remove(a);
        if(wasDefault)all.stream().filter(v->!v.getId().equals(id)).findFirst().ifPresent(v->v.setDefaultAddress(true));
    }
    private List<Address> ownedAddresses(Long uid){return em.createQuery("select a from CustomerAddress a where a.userId=:uid order by a.defaultAddress desc,a.id desc",Address.class).setParameter("uid",uid).getResultList();}
    private AddressView addressView(Address a){return new AddressView(a.getId(),a.getRecipientName(),a.getPhone(),a.getAddress(),a.getWard(),a.getDistrict(),a.getCity(),a.isDefaultAddress());}

    @Transactional public void changePassword(PasswordChange c){
        User u=lockCurrent(); if(!encoder.matches(c.currentPassword(),u.getPasswordHash()))throw new BusinessException("Mật khẩu hiện tại không đúng");
        setPassword(u,c.newPassword());
    }
    @Transactional public void forgotPassword(String email){
        if(!mailEnabled || mail.isEmpty())throw new BusinessException("Khôi phục qua email chưa được cấu hình. Vui lòng liên hệ bộ phận hỗ trợ");
        var found=users.findByEmailIgnoreCase(email.trim());
        if(found.isEmpty() || Boolean.TRUE.equals(found.get().getIsDeleted()) || found.get().getStatus()!=AccountStatus.ACTIVE)return;
        User u=users.findByIdForUpdate(found.get().getId()).orElseThrow();
        Instant now=Instant.now();
        var recent=em.createQuery("select r from PasswordRecovery r where r.userId=:uid and r.createdAt>:time",PasswordRecovery.class)
                .setParameter("uid",u.getId()).setParameter("time",now.minusSeconds(60)).setMaxResults(1).getResultList();
        if(!recent.isEmpty())return;
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var record=new PasswordRecovery(); record.setTokenHash(hash(token)); record.setUserId(u.getId()); record.setCreatedAt(now); record.setExpiresAt(now.plusSeconds(1800)); em.persist(record);
        SimpleMailMessage message=new SimpleMailMessage(); message.setFrom(from); message.setTo(u.getEmail());
        message.setSubject("Đặt lại mật khẩu BeautyShop");
        message.setText("Bạn đã yêu cầu đặt lại mật khẩu. Liên kết có hiệu lực 30 phút và chỉ dùng một lần:\n"+customerUrl.replaceAll("/$","")+"/#/reset-password?token="+token+"\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
        try {mail.get().send(message);} catch(org.springframework.mail.MailException ex){
            // Keep the same public response for registered and unregistered emails.
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Password recovery mail delivery failed; check SMTP configuration");
            em.remove(record);
        }
    }
    @Transactional public void resetPassword(PasswordReset c){
        var r=em.find(PasswordRecovery.class,hash(c.token()));
        if(r==null)throw new BusinessException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        User u=users.findByIdForUpdate(r.getUserId()).orElseThrow(()->new BusinessException("Liên kết không hợp lệ"));
        em.refresh(r,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(r.getUsedAt()!=null || !r.getExpiresAt().isAfter(Instant.now()) || u.getStatus()!=AccountStatus.ACTIVE)throw new BusinessException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        setPassword(u,c.newPassword());
        em.createQuery("update PasswordRecovery r set r.usedAt=:now where r.userId=:uid and r.usedAt is null").setParameter("now",Instant.now()).setParameter("uid",u.getId()).executeUpdate();
    }
    private void setPassword(User u,String password){
        if(password==null || password.length()<8 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new BusinessException("Mật khẩu mới cần ít nhất 8 ký tự và tối đa 72 byte UTF-8");
        if(encoder.matches(password,u.getPasswordHash()))throw new BusinessException("Mật khẩu mới phải khác mật khẩu hiện tại");
        u.setPasswordHash(encoder.encode(password)); users.save(u); auth.forceLogoutUser(u.getId());
    }
    @Transactional public void subscribe(String email, boolean consent){
        if(!consent)throw new BusinessException("Cần đồng ý nhận bản tin");
        String normalized=email.trim().toLowerCase(Locale.ROOT);
        jdbc.update("INSERT INTO newsletter_subscriptions(email,consented_at,active) VALUES (?,?,TRUE) ON DUPLICATE KEY UPDATE consented_at=VALUES(consented_at),active=TRUE",
                normalized,java.sql.Timestamp.from(Instant.now()));
    }
    @Transactional(readOnly=true) public OrderStatistics statistics(){
        return jdbc.queryForObject("select count(*) cnt,coalesce(sum(paid_amount),0) paid,coalesce(sum(refunded_amount),0) refunded from orders where user_id=? and is_deleted=false",
            (rs,n)->new OrderStatistics(rs.getLong("cnt"),rs.getBigDecimal("paid"),rs.getBigDecimal("refunded"),rs.getBigDecimal("paid").subtract(rs.getBigDecimal("refunded"))),SecurityUtils.getCurrentUserId());
    }
    private User lockCurrent(){return users.findByIdForUpdate(SecurityUtils.getCurrentUserId()).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy tài khoản"));}
    private static String clean(String value){return value==null || value.isBlank()?null:value.trim();}
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}

    public record SkinCommand(@Size(max=100) String skinType,@Size(max=100) String sensitivity,@Size(max=1000) String allergies,@Size(max=1000) String contraindications,@Size(max=1000) String preferredIngredients,@Size(max=2000) String notes){}
    public record SkinView(String skinType,String sensitivity,String allergies,String contraindications,String preferredIngredients,String notes,Instant updatedAt,boolean declaredByCustomer){}
    public record AddressCommand(@NotBlank @Size(max=100) String recipientName,@NotBlank @Pattern(regexp="^(0|\\+84)[35789][0-9]{8}$") String phone,@NotBlank @Size(max=500) String address,@NotBlank @Size(max=100) String ward,@Size(max=100) String district,@NotBlank @Size(max=100) String city,boolean isDefault){}
    public record AddressView(Long id,String recipientName,String phone,String address,String ward,String district,String city,boolean isDefault){}
    public record PasswordChange(@NotBlank @Size(max=128) String currentPassword,@NotBlank @Size(min=8,max=128) String newPassword){}
    public record PasswordReset(@NotBlank @Size(max=256) String token,@NotBlank @Size(min=8,max=128) String newPassword){}
    public record EmailCommand(@NotBlank @Email @Size(max=254) String email){}
    public record NewsletterCommand(@NotBlank @Email @Size(max=254) String email,boolean consent){}
    public record OrderStatistics(long totalOrders,BigDecimal totalPaid,BigDecimal totalRefunded,BigDecimal netSpent){}
}
