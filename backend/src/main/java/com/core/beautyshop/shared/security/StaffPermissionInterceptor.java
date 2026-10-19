package com.core.beautyshop.shared.security;

import com.core.beautyshop.modules.identity.api.StaffPermissionService;
import com.core.beautyshop.shared.security.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.List;

@Component @RequiredArgsConstructor
public class StaffPermissionInterceptor implements HandlerInterceptor {
    private final StaffPermissionService permissions;
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
        if(SecurityUtils.isAdmin() || !SecurityUtils.isStaffOrAdmin() || "OPTIONS".equals(request.getMethod()))return true;
        String p=request.getRequestURI().substring(request.getContextPath().length());
        String required=requiredPermission(p,request.getMethod());
        if(required==null)return true;
        boolean granted=List.of(required.split("\\|")).stream().anyMatch(permissions::has);
        if(!granted)throw new AccessDeniedException("Bạn chưa được phân quyền nghiệp vụ cho thao tác này");
        return true;
    }
    public static String requiredPermission(String p,String method){
        if(p.startsWith("/api/v1/admin/commerce") || p.startsWith("/api/v1/admin/orders"))return "ORDERS";
        if(p.startsWith("/api/v1/admin/inventory") || p.startsWith("/api/v1/admin/warehouses"))return "INVENTORY";
        if(p.startsWith("/api/v1/admin/procurement"))return "PROCUREMENT";
        if(p.startsWith("/api/v1/admin/crm"))return "CRM";
        if(p.startsWith("/api/v1/admin/dashboard"))return "REPORTS";
        if(p.startsWith("/api/v1/admin/staff") || p.startsWith("/api/v1/admin/spa"))return "SPA_RECEPTION";
        if(p.startsWith("/api/v1/staff/spa/treatments"))return "SPA_RECEPTION|SPA_EXECUTION";
        if("GET".equals(method) && p.equals("/api/v1/appointments/admin/all"))return "SPA_RECEPTION|SPA_EXECUTION";
        if("PUT".equals(method) && p.matches("/api/v1/appointments/admin/[0-9]+/status"))return "SPA_RECEPTION|SPA_EXECUTION";
        if(p.startsWith("/api/v1/appointments/admin"))return "SPA_RECEPTION";
        if(p.startsWith("/api/v1/appointments/") && p.endsWith("/execution"))return "SPA_EXECUTION";
        if(p.startsWith("/api/v1/appointments/") && p.endsWith("/check-in"))return "SPA_RECEPTION";
        if(!"GET".equals(method) && p.startsWith("/api/v1/appointments/") && (p.endsWith("/invoice") || p.endsWith("/cash-receipts")))return "SPA_RECEPTION";
        if(p.startsWith("/api/v1/spa/appointments/") && (p.endsWith("/deposit-receipts") || p.endsWith("/fee-receipts")))return "SPA_RECEPTION";
        if(p.startsWith("/api/v1/spa/appointments/") && (p.endsWith("/material-usages") || p.endsWith("/material-waiver") || p.endsWith("/acknowledge-warnings")))return "SPA_EXECUTION";
        if(p.equals("/api/v1/orders") || (p.startsWith("/api/v1/orders/") && p.endsWith("/status")))return "ORDERS";
        if(p.startsWith("/api/v1/admin/") && List.of("products","media","ingredients","dermatology","banners","reviews","vouchers","recommendations").stream().anyMatch(s->p.startsWith("/api/v1/admin/"+s)))return "CATALOG";
        if(!"GET".equals(method) && List.of("categories","brands","attributes","tags").stream().anyMatch(s->p.startsWith("/api/v1/"+s)))return "CATALOG";
        return null;
    }
}
