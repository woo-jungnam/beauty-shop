package com.core.beautyshop.modules.inventory.application.service;
import com.core.beautyshop.modules.inventory.api.dto.SpaMaterialOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class SpaMaterialOptionsService {
 private final JdbcTemplate jdbc;
 @Transactional(readOnly=true)public List<SpaMaterialOption> list(){return jdbc.query("SELECT w.id warehouse_id,w.name warehouse_name,pv.id variant_id,pv.sku,pv.variant_name,p.name product_name,SUM(s.quantity-s.reserved_quantity) available_quantity FROM warehouse_stocks s JOIN warehouses w ON w.id=s.warehouse_id JOIN product_variants pv ON pv.id=s.product_variant_id JOIN products p ON p.id=pv.product_id WHERE s.is_deleted=false AND s.is_blocked=false AND w.is_deleted=false AND w.is_active=true AND pv.is_deleted=false AND p.is_deleted=false AND (s.expiration_date IS NULL OR s.expiration_date>?) GROUP BY w.id,w.name,pv.id,pv.sku,pv.variant_name,p.name HAVING SUM(s.quantity-s.reserved_quantity)>0 ORDER BY w.name,p.name,pv.sku",(rs,n)->new SpaMaterialOption(rs.getLong("warehouse_id"),rs.getString("warehouse_name"),rs.getLong("variant_id"),rs.getString("sku"),rs.getString("variant_name"),rs.getString("product_name"),rs.getLong("available_quantity")),com.core.beautyshop.modules.inventory.api.InventoryExpiryPolicy.saleCutoff());}
}
