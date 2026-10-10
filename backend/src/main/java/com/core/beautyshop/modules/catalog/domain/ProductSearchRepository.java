package com.core.beautyshop.modules.catalog.domain;

import com.core.beautyshop.modules.catalog.application.dto.request.ProductSearchRequest;
import com.core.beautyshop.modules.catalog.application.dto.response.ProductListResponse;
import com.core.beautyshop.modules.catalog.domain.enums.ProductStatus;
import com.core.beautyshop.modules.catalog.domain.enums.SkinType;
import com.core.beautyshop.modules.inventory.api.InventoryFacade;
import com.core.beautyshop.shared.exception.BusinessException;
import com.core.beautyshop.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Public catalog filters run before pagination; to-many relationships use EXISTS to avoid duplicate products. */
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {
    private final EntityManager entityManager;
    private final InventoryFacade inventoryFacade;

    public Page<ProductListResponse> search(ProductSearchRequest request, Pageable pageable) {
        if (request.getMinPrice() != null && request.getMaxPrice() != null && request.getMinPrice().compareTo(request.getMaxPrice()) > 0) {
            throw invalid("maxPrice must be greater than or equal to minPrice");
        }
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<ProductListResponse> query = builder.createQuery(ProductListResponse.class);
        Root<Product> product = query.from(Product.class);
        Join<Product, Brand> brand = visibleBrand(builder, product);
        Expression<BigDecimal> minPrice = builder.coalesce(variantPrice(builder, query, product, false), BigDecimal.ZERO);
        Expression<BigDecimal> maxPrice = builder.coalesce(variantPrice(builder, query, product, true), BigDecimal.ZERO);
        query.select(builder.construct(ProductListResponse.class,
                product.get("id"), product.get("name"), product.get("slug"), product.get("shortDescription"), product.get("thumbnailUrl"),
                minPrice, maxPrice, product.get("status"), product.get("isFeatured"), product.get("averageRating"),
                product.get("totalReviews"), product.get("totalSold"), brand.get("name"), brand.get("id"),
                firstCategoryId(builder, query, product)));
        query.where(filters(builder, query, product, brand, request).toArray(Predicate[]::new));
        query.orderBy(orders(builder, product, minPrice, maxPrice, pageable.getSort()));

        if (pageable.isPaged() && pageable.getOffset() > Integer.MAX_VALUE) {
            throw invalid("Page offset exceeds the supported range");
        }
        var selection = entityManager.createQuery(query);
        if (pageable.isPaged()) selection.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        List<ProductListResponse> content = selection.getResultList();

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Product> countProduct = countQuery.from(Product.class);
        Join<Product, Brand> countBrand = visibleBrand(builder, countProduct);
        countQuery.select(builder.count(countProduct));
        countQuery.where(filters(builder, countQuery, countProduct, countBrand, request).toArray(Predicate[]::new));
        return new PageImpl<>(content, pageable, entityManager.createQuery(countQuery).getSingleResult());
    }

    private Join<Product, Brand> visibleBrand(CriteriaBuilder builder, Root<Product> product) {
        Join<Product, Brand> brand = product.join("brand", JoinType.LEFT);
        brand.on(builder.isFalse(brand.get("isDeleted")));
        return brand;
    }

    private List<Predicate> filters(CriteriaBuilder builder, CommonAbstractCriteria query, Root<Product> product,
                                    Join<Product, Brand> brand, ProductSearchRequest request) {
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(builder.isFalse(product.get("isDeleted")));
        conditions.add(builder.equal(product.get("status"), ProductStatus.ACTIVE));

        String keyword = request.getKeyword() == null ? "" : request.getKeyword().trim();
        if (!keyword.isEmpty()) {
            String literal = "%" + keyword.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            Subquery<Integer> matchingVariant = query.subquery(Integer.class);
            Root<ProductVariant> variant = matchingVariant.from(ProductVariant.class);
            matchingVariant.select(builder.literal(1)).where(activeVariant(builder, variant, product), builder.or(
                    like(builder, variant.get("sku"), literal), like(builder, variant.get("barcode"), literal),
                    like(builder, variant.get("variantName"), literal)));
            Subquery<Integer> matchingIngredient = query.subquery(Integer.class);
            Root<ProductIngredient> ingredient = matchingIngredient.from(ProductIngredient.class);
            matchingIngredient.select(builder.literal(1)).where(builder.equal(ingredient.get("product"), product),
                    builder.isFalse(ingredient.get("ingredient").get("isDeleted")), builder.or(
                            like(builder, ingredient.get("ingredient").get("name"), literal),
                            like(builder, ingredient.get("ingredient").get("inciName"), literal),
                            like(builder, ingredient.get("ingredient").get("slug"), literal)));
            Subquery<Integer> matchingTag = query.subquery(Integer.class);
            Root<Product> taggedProduct = matchingTag.from(Product.class);
            Join<Product, ProductTag> tag = taggedProduct.join("tags");
            matchingTag.select(builder.literal(1)).where(builder.equal(taggedProduct.get("id"), product.get("id")),
                    builder.isFalse(tag.get("isDeleted")), builder.or(like(builder, tag.get("name"), literal), like(builder, tag.get("slug"), literal)));
            conditions.add(builder.or(like(builder, product.get("name"), literal), like(builder, product.get("slug"), literal),
                    like(builder, product.get("shortDescription"), literal), like(builder, product.get("description"), literal),
                    like(builder, product.get("ingredients"), literal), like(builder, product.get("keyActivesSummary"), literal),
                    like(builder, brand.get("name"), literal), builder.exists(matchingVariant),
                    builder.exists(matchingIngredient), builder.exists(matchingTag)));
        }

        Set<Long> brands = ids(request.getBrandId(), request.getBrandIds());
        if (!brands.isEmpty()) conditions.add(brand.get("id").in(brands));
        Set<Long> categories = ids(request.getCategoryId(), request.getCategoryIds());
        if (!categories.isEmpty()) {
            Subquery<Integer> categoryQuery = query.subquery(Integer.class);
            Root<Product> categoryProduct = categoryQuery.from(Product.class);
            Join<Product, Category> category = categoryProduct.join("categories");
            categoryQuery.select(builder.literal(1)).where(builder.equal(categoryProduct.get("id"), product.get("id")),
                    category.get("id").in(categories), builder.isFalse(category.get("isDeleted")), builder.isTrue(category.get("isActive")));
            conditions.add(builder.exists(categoryQuery));
        }
        if (present(request.getTagIds())) {
            Subquery<Integer> tagQuery = query.subquery(Integer.class);
            Root<Product> tagProduct = tagQuery.from(Product.class);
            Join<Product, ProductTag> tag = tagProduct.join("tags");
            tagQuery.select(builder.literal(1)).where(builder.equal(tagProduct.get("id"), product.get("id")),
                    tag.get("id").in(request.getTagIds()), builder.isFalse(tag.get("isDeleted")));
            conditions.add(builder.exists(tagQuery));
        }
        if (present(request.getIngredientIds())) {
            Subquery<Integer> ingredientQuery = query.subquery(Integer.class);
            Root<ProductIngredient> ingredient = ingredientQuery.from(ProductIngredient.class);
            ingredientQuery.select(builder.literal(1)).where(builder.equal(ingredient.get("product"), product),
                    ingredient.get("ingredient").get("id").in(request.getIngredientIds()),
                    builder.isFalse(ingredient.get("ingredient").get("isDeleted")));
            conditions.add(builder.exists(ingredientQuery));
        }
        if (present(request.getSkinConcernIds())) {
            Subquery<Integer> concernQuery = query.subquery(Integer.class);
            Root<ProductSkinConcern> concern = concernQuery.from(ProductSkinConcern.class);
            concernQuery.select(builder.literal(1)).where(builder.equal(concern.get("product"), product),
                    concern.get("concern").get("id").in(request.getSkinConcernIds()));
            conditions.add(builder.exists(concernQuery));
        }
        if (request.getSkinType() != null) conditions.add(skinType(builder, query, product, request.getSkinType()));
        if (request.getProductType() != null) conditions.add(builder.equal(product.get("productType"), request.getProductType()));
        if (request.getTargetGender() != null) conditions.add(builder.equal(product.get("targetGender"), request.getTargetGender()));
        if (request.getIsFeatured() != null) conditions.add(builder.equal(product.get("isFeatured"), request.getIsFeatured()));
        if (request.getHasFragrance() != null) conditions.add(builder.equal(product.get("hasFragrance"), request.getHasFragrance()));
        if (request.getHasAlcohol() != null) conditions.add(builder.equal(product.get("hasAlcohol"), request.getHasAlcohol()));
        if (request.getMinRating() != null) conditions.add(builder.greaterThanOrEqualTo(product.<Double>get("averageRating"), request.getMinRating().doubleValue()));
        if (request.getOriginCountry() != null && !request.getOriginCountry().isBlank()) {
            conditions.add(builder.equal(builder.lower(product.get("originCountry")), request.getOriginCountry().trim().toLowerCase(Locale.ROOT)));
        }

        if (request.getMinPrice() != null || request.getMaxPrice() != null || request.getOnSale() != null || Boolean.TRUE.equals(request.getInStock())) {
            Subquery<Integer> variantQuery = query.subquery(Integer.class);
            Root<ProductVariant> variant = variantQuery.from(ProductVariant.class);
            List<Predicate> variantConditions = new ArrayList<>();
            variantConditions.add(activeVariant(builder, variant, product));
            Expression<BigDecimal> price = effectivePrice(builder, variant);
            if (request.getMinPrice() != null) variantConditions.add(builder.greaterThanOrEqualTo(price, request.getMinPrice()));
            if (request.getMaxPrice() != null) variantConditions.add(builder.lessThanOrEqualTo(price, request.getMaxPrice()));
            if (request.getOnSale() != null) {
                Predicate sale = builder.and(builder.isNotNull(variant.get("discountPrice")), builder.lessThan(variant.<BigDecimal>get("discountPrice"), variant.<BigDecimal>get("price")));
                variantConditions.add(Boolean.TRUE.equals(request.getOnSale()) ? sale : builder.not(sale));
            }
            if (Boolean.TRUE.equals(request.getInStock())) variantConditions.add(inventoryFacade.hasAvailableStock(builder, variantQuery, variant.get("id")));
            variantQuery.select(builder.literal(1)).where(variantConditions.toArray(Predicate[]::new));
            conditions.add(builder.exists(variantQuery));
        }
        if (Boolean.FALSE.equals(request.getInStock())) {
            Subquery<Integer> stockedVariant = query.subquery(Integer.class);
            Root<ProductVariant> variant = stockedVariant.from(ProductVariant.class);
            stockedVariant.select(builder.literal(1)).where(activeVariant(builder, variant, product),
                    inventoryFacade.hasAvailableStock(builder, stockedVariant, variant.get("id")));
            conditions.add(builder.not(builder.exists(stockedVariant)));
        }
        return conditions;
    }

    private Predicate skinType(CriteriaBuilder builder, CommonAbstractCriteria query, Root<Product> product, SkinType skinType) {
        Subquery<Integer> recommended = compatibility(builder, query, product, skinType.name(), true);
        Subquery<Integer> notRecommended = compatibility(builder, query, product, skinType.name(), false);
        Predicate legacy = builder.or(builder.equal(product.get("skinType"), skinType), builder.equal(product.get("skinType"), SkinType.ALL_SKIN));
        return builder.and(builder.not(builder.exists(notRecommended)), builder.or(builder.exists(recommended), legacy));
    }

    private Subquery<Integer> compatibility(CriteriaBuilder builder, CommonAbstractCriteria query, Root<Product> product, String code, boolean recommended) {
        Subquery<Integer> compatibility = query.subquery(Integer.class);
        Root<ProductSkinCompatibility> item = compatibility.from(ProductSkinCompatibility.class);
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(builder.equal(item.get("product"), product));
        conditions.add(builder.equal(item.get("isRecommended"), recommended));
        // ALL_SKIN is a broad legacy claim: any explicit negative compatibility invalidates it.
        // The canonical dictionary normally contains individual skin types and no ALL_SKIN row.
        if (recommended || !SkinType.ALL_SKIN.name().equals(code)) {
            conditions.add(builder.equal(item.get("skinType").get("code"), code));
        }
        compatibility.select(builder.literal(1)).where(conditions.toArray(Predicate[]::new));
        return compatibility;
    }

    private Predicate activeVariant(CriteriaBuilder builder, Root<ProductVariant> variant, Root<Product> product) {
        return builder.and(builder.equal(variant.get("product"), product), builder.isFalse(variant.get("isDeleted")), builder.isTrue(variant.get("isActive")));
    }

    private Expression<BigDecimal> effectivePrice(CriteriaBuilder builder, Root<ProductVariant> variant) {
        return builder.coalesce(variant.<BigDecimal>get("discountPrice"), variant.<BigDecimal>get("price"));
    }

    private Subquery<BigDecimal> variantPrice(CriteriaBuilder builder, CommonAbstractCriteria query, Root<Product> product, boolean maximum) {
        Subquery<BigDecimal> priceQuery = query.subquery(BigDecimal.class);
        Root<ProductVariant> variant = priceQuery.from(ProductVariant.class);
        priceQuery.select(maximum ? builder.max(effectivePrice(builder, variant)) : builder.min(effectivePrice(builder, variant)))
                .where(activeVariant(builder, variant, product));
        return priceQuery;
    }

    private Subquery<Long> firstCategoryId(CriteriaBuilder builder, CommonAbstractCriteria query, Root<Product> product) {
        Subquery<Long> categoryQuery = query.subquery(Long.class);
        Root<Product> categoryProduct = categoryQuery.from(Product.class);
        Join<Product, Category> category = categoryProduct.join("categories");
        categoryQuery.select(builder.min(category.<Long>get("id"))).where(builder.equal(categoryProduct.get("id"), product.get("id")),
                builder.isFalse(category.get("isDeleted")), builder.isTrue(category.get("isActive")));
        return categoryQuery;
    }

    private List<Order> orders(CriteriaBuilder builder, Root<Product> product, Expression<BigDecimal> minPrice,
                                Expression<BigDecimal> maxPrice, Sort sort) {
        List<Order> orders = new ArrayList<>();
        boolean hasId = false;
        for (Sort.Order order : sort) {
            Expression<?> expression = switch (order.getProperty()) {
                case "price", "minPrice" -> minPrice;
                case "maxPrice" -> maxPrice;
                case "name" -> builder.lower(product.get("name"));
                case "id", "createdAt", "updatedAt", "averageRating", "totalSold" -> product.get(order.getProperty());
                default -> throw invalid("Unsupported product sort: " + order.getProperty());
            };
            if (order.isIgnoreCase() && !order.getProperty().equals("name")) throw invalid("Ignore-case sorting is supported only for name");
            orders.add(order.isAscending() ? builder.asc(expression) : builder.desc(expression));
            if (order.getProperty().equals("id")) hasId = true;
        }
        if (orders.isEmpty()) orders.add(builder.desc(product.get("createdAt")));
        if (!hasId) orders.add(builder.desc(product.get("id")));
        return orders;
    }

    private Predicate like(CriteriaBuilder builder, Expression<String> field, String pattern) {
        return builder.like(builder.lower(field), pattern, '!');
    }

    private Set<Long> ids(Long alias, Collection<Long> list) {
        Set<Long> ids = new LinkedHashSet<>();
        if (alias != null) ids.add(alias);
        if (list != null) ids.addAll(list);
        return ids;
    }

    private boolean present(Collection<?> values) { return values != null && !values.isEmpty(); }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.INVALID_PARAMETER, message); }
}
