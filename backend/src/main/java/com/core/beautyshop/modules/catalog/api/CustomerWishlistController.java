package com.core.beautyshop.modules.catalog.api;

import com.core.beautyshop.modules.catalog.domain.CustomerWishlistEntry;
import com.core.beautyshop.modules.catalog.application.service.ProductService;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductResponse;
import com.core.beautyshop.shared.dto.ApiResponse;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/users/wishlist")
@PreAuthorize("isAuthenticated()")
public class CustomerWishlistController {
    private final EntityManager em;
    private final ProductService products;
    @GetMapping @Transactional(readOnly=true) public ApiResponse<List<ProductResponse>> list(){
        var ids=em.createQuery("select w.productId from CustomerWishlistEntry w where w.userId=:uid order by w.id desc",Long.class).setParameter("uid",SecurityUtils.getCurrentUserId()).getResultList();
        List<ProductResponse> result=new ArrayList<>();
        for(Long id:ids){try{result.add(products.getProductById(id));}catch(ResourceNotFoundException | BusinessException ignored){}}
        return ApiResponse.success(result);
    }
    @PutMapping("/{productId}") @Transactional public ApiResponse<Void> add(@PathVariable Long productId){
        Long uid=SecurityUtils.getCurrentUserId();
        em.createNativeQuery("select id from users where id=:uid for update").setParameter("uid",uid).getSingleResult();
        products.getProductById(productId);
        Long count=em.createQuery("select count(w) from CustomerWishlistEntry w where w.userId=:uid and w.productId=:pid",Long.class).setParameter("uid",uid).setParameter("pid",productId).getSingleResult();
        if(count==0){
            long total=em.createQuery("select count(w) from CustomerWishlistEntry w where w.userId=:uid",Long.class).setParameter("uid",uid).getSingleResult();
            if(total>=200)throw new BusinessException("Danh sách yêu thích tối đa 200 sản phẩm");
            var w=new CustomerWishlistEntry();w.setUserId(uid);w.setProductId(productId);w.setCreatedAt(Instant.now());em.persist(w);
        }
        return ApiResponse.success(null);
    }
    @DeleteMapping("/{productId}") @Transactional public ApiResponse<Void> remove(@PathVariable Long productId){
        em.createQuery("delete from CustomerWishlistEntry w where w.userId=:uid and w.productId=:pid").setParameter("uid",SecurityUtils.getCurrentUserId()).setParameter("pid",productId).executeUpdate();return ApiResponse.success(null);
    }
}
