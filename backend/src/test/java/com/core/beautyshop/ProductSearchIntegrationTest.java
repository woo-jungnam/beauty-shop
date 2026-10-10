package com.core.beautyshop;

import com.core.beautyshop.modules.catalog.domain.*;
import com.core.beautyshop.modules.catalog.domain.enums.*;
import com.core.beautyshop.modules.inventory.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The same HTTP/query invariants run on H2 and the migrated MySQL schema in the subclass. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:product_search;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductSearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired ProductTagRepository tags;
    @Autowired IngredientRepository ingredients;
    @Autowired ProductIngredientRepository productIngredients;
    @Autowired SkinConcernRepository concerns;
    @Autowired ProductSkinConcernRepository productConcerns;
    @Autowired SkinTypeEntityRepository skinTypes;
    @Autowired ProductSkinCompatibilityRepository compatibility;
    @Autowired WarehouseRepository warehouses;
    @Autowired WarehouseStockRepository stocks;
    String prefix;
    int sequence;

    @BeforeEach void isolateFixtures() {
        prefix = "ps" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        sequence = 0;
        caches.getCacheNames().forEach(name -> Objects.requireNonNull(caches.getCache(name)).clear());
    }

    Product product(String name) {
        return products.saveAndFlush(Product.builder().name(prefix + " " + name).slug(prefix + "-p" + ++sequence)
                .status(ProductStatus.ACTIVE).productType(ProductType.PRODUCT).skinType(SkinType.OILY)
                .targetGender(TargetGender.UNISEX).averageRating(4.5).isFeatured(true)
                .hasFragrance(false).hasAlcohol(false).originCountry("France").build());
    }
    ProductVariant variant(Product product, String price, String discount) {
        return variants.saveAndFlush(ProductVariant.builder().product(product).sku(prefix + "-s" + ++sequence)
                .variantName("Default").price(new BigDecimal(price))
                .discountPrice(discount == null ? null : new BigDecimal(discount)).isActive(true).build());
    }
    Brand brand() { return brands.saveAndFlush(Brand.builder().name(prefix + " brand " + ++sequence).slug(prefix + "-b" + sequence).build()); }
    Category category() { return categories.saveAndFlush(Category.builder().name(prefix + " category " + ++sequence).slug(prefix + "-c" + sequence).build()); }
    ProductTag tag() { return tags.saveAndFlush(ProductTag.builder().name(prefix + " tag " + ++sequence).slug(prefix + "-t" + sequence).build()); }
    Ingredient ingredient() { return ingredients.saveAndFlush(Ingredient.builder().name(prefix + " ingredient " + ++sequence)
            .inciName("INCI " + sequence).slug(prefix + "-i" + sequence).build()); }
    SkinConcern concern() { return concerns.saveAndFlush(SkinConcern.builder().name(prefix + " concern " + ++sequence).code(prefix + "-sc" + sequence).build()); }
    void attachIngredient(Product product, Ingredient ingredient) { productIngredients.saveAndFlush(ProductIngredient.builder().product(product).ingredient(ingredient).build()); }
    void attachConcern(Product product, SkinConcern concern) { productConcerns.saveAndFlush(ProductSkinConcern.builder().product(product).concern(concern).build()); }
    Warehouse warehouse() { return warehouses.saveAndFlush(Warehouse.builder().name(prefix + " warehouse " + ++sequence).code(prefix + "-w" + sequence).build()); }
    WarehouseStock stock(ProductVariant variant, Warehouse warehouse, int quantity, int reserved, int quarantined, LocalDate expiration) {
        return stocks.saveAndFlush(WarehouseStock.builder().warehouse(warehouse).productVariantId(variant.getId())
                .batchCode(prefix + "-lot" + ++sequence).quantity(quantity).reservedQuantity(reserved)
                .quarantinedQuantity(quarantined).expirationDate(expiration).build());
    }
    void available(ProductVariant variant) { stock(variant, warehouse(), 3, 1, 0, null); }
    MockHttpServletRequestBuilder search() { return get("/api/v1/products/search").param("keyword", prefix); }
    JsonNode page(MockHttpServletRequestBuilder request) throws Exception {
        entities.flush(); entities.clear();
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
    List<Long> ids(JsonNode page) {
        List<Long> result = new ArrayList<>();
        page.path("content").forEach(product -> result.add(product.path("id").asLong()));
        return result;
    }
    void exactly(JsonNode page, Product... expected) {
        Set<Long> wanted = new HashSet<>(Arrays.stream(expected).map(Product::getId).toList());
        assertEquals(wanted, new HashSet<>(ids(page)), page.toString());
        assertEquals(expected.length, page.path("totalElements").asLong(), page.toString());
        assertEquals(ids(page).size(), new HashSet<>(ids(page)).size(), "A product must not occur twice");
    }
    String idList(Long... values) { return String.join(",", Arrays.stream(values).map(String::valueOf).toList()); }

    @Test void anonymousSearchAllowsEmptyCriteriaAndNoSkuButAlwaysHidesInactiveAndDeletedProducts() throws Exception {
        Product visible = product("No SKU yet");
        Product inactive = product("Inactive"); inactive.setStatus(ProductStatus.INACTIVE);
        Product deleted = product("Deleted"); deleted.setIsDeleted(true);
        exactly(page(search()), visible);
        JsonNode emptyCriteria = page(get("/api/v1/products/search").param("sort", "id,desc"));
        assertTrue(ids(emptyCriteria).contains(visible.getId()));
        assertFalse(ids(emptyCriteria).contains(inactive.getId()));
        assertFalse(ids(emptyCriteria).contains(deleted.getId()));
        assertEquals(0, emptyCriteria.path("page").asInt()); assertEquals(20, emptyCriteria.path("size").asInt());
        Brand onlyBrand = brand();
        Product blankKeyword = product("Blank keyword"); blankKeyword.setBrand(onlyBrand);
        exactly(page(get("/api/v1/products/search").param("keyword", "   ").param("brandId", onlyBrand.getId().toString())), blankKeyword);
    }

    @Test void keywordTreatsPercentUnderscoreBackslashAndEscapeMarkerAsLiteralCharacters() throws Exception {
        for (String literal : List.of("%", "_", "\\", "!")) {
            String token = prefix + literal + "literal";
            Product match = product("Placeholder"); match.setName(token);
            Product decoy = product("Decoy"); decoy.setName(prefix + "Xliteral");
            exactly(page(get("/api/v1/products/search").param("keyword", token)), match);
        }
    }

    @Test void keywordMatchesEveryDocumentedFieldAndOnlyActiveNondeletedSkuFields() throws Exception {
        String needle = prefix + "Needle";
        Product byName = product("By name"); byName.setName(needle);
        Product bySlug = product("By slug"); bySlug.setSlug(needle.toLowerCase(Locale.ROOT));
        Product byDescription = product("By description"); byDescription.setShortDescription(needle);
        Brand namedBrand = brand(); namedBrand.setName(needle);
        Product byBrand = product("By brand"); byBrand.setBrand(namedBrand);
        Product bySku = product("By SKU"); variant(bySku, "100", null).setSku(needle);
        Product byBarcode = product("By barcode"); variant(byBarcode, "100", null).setBarcode(needle);
        Product byVariantName = product("By variant name"); variant(byVariantName, "100", null).setVariantName(needle);
        Product byFullDescription = product("By full description"); byFullDescription.setDescription(needle);
        Product byIngredientsText = product("By ingredients text"); byIngredientsText.setIngredients(needle);
        Product byActivesSummary = product("By actives summary"); byActivesSummary.setKeyActivesSummary(needle);
        Product byIngredientName = product("By ingredient name"); Ingredient ingredientName = ingredient(); ingredientName.setName(needle); attachIngredient(byIngredientName, ingredientName);
        Product byInci = product("By INCI"); Ingredient ingredientInci = ingredient(); ingredientInci.setInciName(needle); attachIngredient(byInci, ingredientInci);
        Product byIngredientSlug = product("By ingredient slug"); Ingredient ingredientSlug = ingredient(); ingredientSlug.setSlug(needle.toLowerCase(Locale.ROOT)); attachIngredient(byIngredientSlug, ingredientSlug);
        Product byTagName = product("By tag name"); ProductTag namedTag = tag(); namedTag.setName(needle); byTagName.setTags(new ArrayList<>(List.of(namedTag)));
        Product byTagSlug = product("By tag slug"); ProductTag slugTag = tag(); slugTag.setSlug(needle.toLowerCase(Locale.ROOT)); byTagSlug.setTags(new ArrayList<>(List.of(slugTag)));
        Product inactive = product("Inactive SKU"); ProductVariant inactiveSku = variant(inactive, "100", null);
        inactiveSku.setSku(needle + "inactive"); inactiveSku.setIsActive(false);
        Product deleted = product("Deleted SKU"); ProductVariant deletedSku = variant(deleted, "100", null);
        deletedSku.setVariantName(needle); deletedSku.setIsDeleted(true);
        Brand deletedBrand = brand(); deletedBrand.setName(needle); deletedBrand.setIsDeleted(true);
        product("Deleted brand").setBrand(deletedBrand);
        Ingredient deletedIngredient = ingredient(); deletedIngredient.setInciName(needle); deletedIngredient.setIsDeleted(true); attachIngredient(product("Deleted ingredient keyword"), deletedIngredient);
        ProductTag deletedTag = tag(); deletedTag.setName(needle + "Deleted"); deletedTag.setIsDeleted(true); product("Deleted tag keyword").setTags(new ArrayList<>(List.of(deletedTag)));
        exactly(page(get("/api/v1/products/search").param("keyword", "  " + needle.toUpperCase(Locale.ROOT) + "  ")),
                byName, bySlug, byDescription, byBrand, bySku, byBarcode, byVariantName,
                byFullDescription, byIngredientsText, byActivesSummary, byIngredientName, byInci, byIngredientSlug, byTagName, byTagSlug);
    }

    @Test void priceSaleAndInStockTrueMustMatchTheSameActiveSku() throws Exception {
        Product matching = product("Matching"); available(variant(matching, "100", "80")); variant(matching, "200", "100");
        Product splitSale = product("Sale and inventory on different SKU");
        variant(splitSale, "100", "80"); available(variant(splitSale, "90", null));
        Product splitBounds = product("Bounds on different SKU"); available(variant(splitBounds, "100", "50")); available(variant(splitBounds, "200", "150"));
        Product inactiveCheap = product("Inactive cheap SKU"); available(variant(inactiveCheap, "200", null));
        ProductVariant hidden = variant(inactiveCheap, "100", "80"); hidden.setIsActive(false); available(hidden);
        ProductVariant removed = variant(inactiveCheap, "100", "80"); removed.setIsDeleted(true); available(removed);
        product("No SKU");
        JsonNode matched = page(search().param("minPrice", "80").param("maxPrice", "80").param("onSale", "true").param("inStock", "true"));
        exactly(matched, matching);
        assertEquals(0, new BigDecimal("80").compareTo(matched.path("content").get(0).path("minPrice").decimalValue()));
        exactly(page(search().param("minPrice", "75").param("maxPrice", "125").param("onSale", "true").param("inStock", "true")), matching);
        exactly(page(search().param("minPrice", "90").param("maxPrice", "90").param("onSale", "false").param("inStock", "true")), splitSale);
    }

    @Test void inStockFalseChecksAllActiveSkuAvailabilitySeparatelyFromThePriceMatch() throws Exception {
        Product empty = product("No stock"); variant(empty, "80", null); variant(empty, "200", null);
        Product hiddenStock = product("Only inactive SKU stock"); variant(hiddenStock, "80", null);
        ProductVariant inactive = variant(hiddenStock, "200", null); inactive.setIsActive(false); available(inactive);
        Product otherSkuAvailable = product("Expensive SKU available"); variant(otherSkuAvailable, "80", null); available(variant(otherSkuAvailable, "200", null));
        Product noSku = product("No SKU");
        exactly(page(search().param("inStock", "false").param("minPrice", "80").param("maxPrice", "80")), empty, hiddenStock);
        exactly(page(search().param("inStock", "false")), empty, hiddenStock, noSku);
    }

    @Test void inventoryFilterAggregatesSellableLotsAndExcludesReservedQuarantineExpiryAndHiddenWarehouses() throws Exception {
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        Product available = product("Available in multiple lots"); ProductVariant availableSku = variant(available, "100", null);
        Warehouse open = warehouse(); stock(availableSku, open, 2, 2, 0, today.plusDays(10)); stock(availableSku, open, 2, 1, 0, null);
        Product reserved = product("Fully reserved"); stock(variant(reserved, "100", null), warehouse(), 3, 3, 0, null);
        Product quarantined = product("Only quarantine bucket"); stock(variant(quarantined, "100", null), warehouse(), 0, 0, 5, null);
        Product separateBuckets = product("Sellable alongside quarantine"); stock(variant(separateBuckets, "100", null), warehouse(), 3, 1, 9, null);
        Product expired = product("Expired"); stock(variant(expired, "100", null), warehouse(), 5, 0, 0, today.minusDays(1));
        Product expiresToday = product("Expires today"); stock(variant(expiresToday, "100", null), warehouse(), 5, 0, 0, today);
        Product paused = product("Inactive warehouse"); Warehouse inactiveWarehouse = warehouse(); inactiveWarehouse.setIsActive(false);
        stock(variant(paused, "100", null), inactiveWarehouse, 5, 0, 0, null);
        Product deletedWarehouseProduct = product("Deleted warehouse"); Warehouse deletedWarehouse = warehouse(); deletedWarehouse.setIsDeleted(true);
        stock(variant(deletedWarehouseProduct, "100", null), deletedWarehouse, 5, 0, 0, null);
        Product deletedLot = product("Deleted lot"); stock(variant(deletedLot, "100", null), warehouse(), 5, 0, 0, null).setIsDeleted(true);
        exactly(page(search().param("inStock", "true")), available, separateBuckets);
    }

    @Test void multivalueDimensionsUseOrInsideAndAcrossDimensionsWithoutDuplicateRowsOrCounts() throws Exception {
        Brand b1 = brand(), b2 = brand(), excludedBrand = brand();
        Category c1 = category(), c2 = category(), excludedCategory = category();
        ProductTag t1 = tag(), t2 = tag(); Ingredient i1 = ingredient(), i2 = ingredient(); SkinConcern sc1 = concern(), sc2 = concern();
        Product first = product("Alpha"); first.setBrand(b1); first.setCategories(new ArrayList<>(List.of(c1, c2))); first.setTags(new ArrayList<>(List.of(t1, t2)));
        attachIngredient(first, i1); attachIngredient(first, i2); attachConcern(first, sc1); attachConcern(first, sc2);
        Product second = product("Beta"); second.setBrand(b2); second.setCategories(new ArrayList<>(List.of(c2))); second.setTags(new ArrayList<>(List.of(t2)));
        attachIngredient(second, i2); attachConcern(second, sc2);
        Product wrongBrand = product("Wrong brand"); wrongBrand.setBrand(excludedBrand); wrongBrand.setCategories(new ArrayList<>(List.of(c1))); wrongBrand.setTags(new ArrayList<>(List.of(t1)));
        attachIngredient(wrongBrand, i1); attachConcern(wrongBrand, sc1);
        Product wrongCategory = product("Wrong category"); wrongCategory.setBrand(b1); wrongCategory.setCategories(new ArrayList<>(List.of(excludedCategory))); wrongCategory.setTags(new ArrayList<>(List.of(t1)));
        attachIngredient(wrongCategory, i1); attachConcern(wrongCategory, sc1);
        Product missingTag = product("No tag"); missingTag.setBrand(b1); missingTag.setCategories(new ArrayList<>(List.of(c1))); attachIngredient(missingTag, i1); attachConcern(missingTag, sc1);
        MockHttpServletRequestBuilder request = dimensionRequest(b1, b2, c1, c2, t1, t2, i1, i2, sc1, sc2);
        JsonNode firstPage = page(request.param("page", "0").param("size", "1").param("sort", "name,asc"));
        assertEquals(List.of(first.getId()), ids(firstPage)); assertEquals(2, firstPage.path("totalElements").asLong());
        assertEquals(2, firstPage.path("totalPages").asInt()); assertFalse(firstPage.path("last").asBoolean());
        JsonNode secondPage = page(dimensionRequest(b1, b2, c1, c2, t1, t2, i1, i2, sc1, sc2).param("page", "1").param("size", "1").param("sort", "name,asc"));
        assertEquals(List.of(second.getId()), ids(secondPage)); assertEquals(2, secondPage.path("totalElements").asLong()); assertTrue(secondPage.path("last").asBoolean());
        exactly(page(search().param("brandId", b1.getId().toString()).param("brandIds", b2.getId().toString())
                .param("categoryId", c1.getId().toString()).param("categoryIds", c2.getId().toString())
                .param("tagIds", t1.getId().toString(), t2.getId().toString()).param("ingredientIds", idList(i1.getId(), i2.getId()))
                .param("skinConcernIds", idList(sc1.getId(), sc2.getId()))), first, second);
    }
    MockHttpServletRequestBuilder dimensionRequest(Brand b1, Brand b2, Category c1, Category c2, ProductTag t1, ProductTag t2, Ingredient i1, Ingredient i2, SkinConcern sc1, SkinConcern sc2) {
        return search().param("brandIds", idList(b1.getId(), b2.getId())).param("categoryIds", idList(c1.getId(), c2.getId()))
                .param("tagIds", idList(t1.getId(), t2.getId())).param("ingredientIds", idList(i1.getId(), i2.getId())).param("skinConcernIds", idList(sc1.getId(), sc2.getId()));
    }

    @Test void scalarFiltersCombineEnumsRatingBooleansAndExactTrimmedOrigin() throws Exception {
        Product matching = product("Matching");
        product("Wrong product type").setProductType(ProductType.COMBO);
        product("Wrong skin").setSkinType(SkinType.DRY);
        product("Wrong gender").setTargetGender(TargetGender.FEMALE);
        product("Low rating").setAverageRating(4.49);
        product("Not featured").setIsFeatured(false);
        product("Fragrance").setHasFragrance(true);
        product("Alcohol").setHasAlcohol(true);
        product("Partial origin").setOriginCountry("France Overseas");
        exactly(page(search().param("productType", "PRODUCT").param("skinType", "OILY").param("targetGender", "UNISEX")
                .param("minRating", "4.5").param("isFeatured", "true").param("hasFragrance", "false").param("hasAlcohol", "false").param("originCountry", "  fRaNcE  ")), matching);
    }

    @Test void explicitSkinContraindicationOverridesLegacyAllSkinAndCanonicalRecommendationCanMatchAnotherLegacyType() throws Exception {
        SkinTypeEntity oily = skinTypes.findByCode("OILY").orElseGet(() -> skinTypes.saveAndFlush(SkinTypeEntity.builder().code("OILY").name("Oily").build()));
        Product legacyAll = product("Legacy all skin"); legacyAll.setSkinType(SkinType.ALL_SKIN);
        Product legacyOily = product("Legacy oily");
        Product canonical = product("Canonical recommendation"); canonical.setSkinType(SkinType.DRY);
        compatibility.saveAndFlush(ProductSkinCompatibility.builder().product(canonical).skinType(oily).isRecommended(true).build());
        Product blockedAll = product("Blocked all skin"); blockedAll.setSkinType(SkinType.ALL_SKIN);
        compatibility.saveAndFlush(ProductSkinCompatibility.builder().product(blockedAll).skinType(oily).isRecommended(false).contraindicationReason("Stored warning").build());
        Product blockedOily = product("Blocked oily");
        compatibility.saveAndFlush(ProductSkinCompatibility.builder().product(blockedOily).skinType(oily).isRecommended(false).build());
        SkinTypeEntity sensitive = skinTypes.findByCode("SENSITIVE").orElseGet(() -> skinTypes.saveAndFlush(SkinTypeEntity.builder().code("SENSITIVE").name("Sensitive").build()));
        Product blockedSensitive = product("All skin with sensitive warning"); blockedSensitive.setSkinType(SkinType.ALL_SKIN);
        compatibility.saveAndFlush(ProductSkinCompatibility.builder().product(blockedSensitive).skinType(sensitive).isRecommended(false).contraindicationReason("Stored sensitive-skin warning").build());
        product("Wrong legacy skin").setSkinType(SkinType.DRY);
        exactly(page(search().param("skinType", "OILY")), legacyAll, legacyOily, canonical, blockedSensitive);
        exactly(page(search().param("skinType", "ALL_SKIN")), legacyAll);
    }

    @Test void hiddenTaxonomyCannotMatchExplicitFiltersAndCategorySelectionDoesNotIncludeDescendants() throws Exception {
        Brand deletedBrand = brand(); deletedBrand.setIsDeleted(true); Product branded = product("Deleted brand"); branded.setBrand(deletedBrand);
        Category hiddenCategory = category(); hiddenCategory.setIsActive(false); Product categorized = product("Inactive category"); categorized.setCategories(new ArrayList<>(List.of(hiddenCategory)));
        Category deletedCategory = category(); deletedCategory.setIsDeleted(true); product("Deleted category").setCategories(new ArrayList<>(List.of(deletedCategory)));
        ProductTag deletedTag = tag(); deletedTag.setIsDeleted(true); product("Deleted tag").setTags(new ArrayList<>(List.of(deletedTag)));
        Ingredient deletedIngredient = ingredient(); deletedIngredient.setIsDeleted(true); attachIngredient(product("Deleted ingredient"), deletedIngredient);
        assertEquals(0, page(search().param("brandId", deletedBrand.getId().toString())).path("totalElements").asLong());
        assertEquals(0, page(search().param("categoryId", hiddenCategory.getId().toString())).path("totalElements").asLong());
        assertEquals(0, page(search().param("categoryIds", deletedCategory.getId().toString())).path("totalElements").asLong());
        assertEquals(0, page(search().param("tagIds", deletedTag.getId().toString())).path("totalElements").asLong());
        assertEquals(0, page(search().param("ingredientIds", deletedIngredient.getId().toString())).path("totalElements").asLong());
        assertEquals(0, page(search().param("brandId", Long.toString(Long.MAX_VALUE))).path("totalElements").asLong());
        Category parent = category(), child = category(); child.setParentCategory(parent);
        Product direct = product("Parent category"); direct.setCategories(new ArrayList<>(List.of(parent)));
        product("Child category").setCategories(new ArrayList<>(List.of(child)));
        exactly(page(search().param("categoryId", parent.getId().toString())), direct);
    }

    List<Product> sortFixture() {
        Product first = product("Alpha"); variant(first, "150", "80"); variant(first, "150", null); first.setAverageRating(5.0); first.setTotalSold(20L);
        Product second = product("Beta"); variant(second, "100", null); second.setAverageRating(4.0); second.setTotalSold(10L);
        Product third = product("Gamma"); variant(third, "100", null); variant(third, "120", null); third.setAverageRating(4.0); third.setTotalSold(10L);
        Product fourth = product("Delta no SKU"); fourth.setAverageRating(0.0); fourth.setTotalSold(0L);
        entities.flush();
        for (Product item : List.of(first, second, third, fourth)) jdbc.update("UPDATE products SET created_at='2026-01-01 00:00:00', updated_at='2026-01-01 00:00:00' WHERE id=?", item.getId());
        return List.of(first, second, third, fourth);
    }
    @Test void computedPriceSortHasStableIdTieBreakerAndCorrectPageMetadataBeyondLastPage() throws Exception {
        List<Product> data = sortFixture(); Product a = data.get(0), b = data.get(1), c = data.get(2), d = data.get(3);
        JsonNode first = page(search().param("sort", "price,asc").param("page", "0").param("size", "2"));
        assertEquals(List.of(d.getId(), a.getId()), ids(first)); assertEquals(4, first.path("totalElements").asLong());
        assertEquals(2, first.path("totalPages").asInt()); assertFalse(first.path("last").asBoolean());
        JsonNode second = page(search().param("sort", "price,asc").param("page", "1").param("size", "2"));
        assertEquals(List.of(c.getId(), b.getId()), ids(second)); assertTrue(second.path("last").asBoolean());
        JsonNode beyond = page(search().param("sort", "price,asc").param("page", "2").param("size", "2"));
        assertTrue(ids(beyond).isEmpty()); assertEquals(4, beyond.path("totalElements").asLong()); assertEquals(2, beyond.path("page").asInt());
        assertEquals(List.of(d.getId(), a.getId(), c.getId(), b.getId()), ids(page(search().param("sort", "minPrice,asc"))));
        assertEquals(List.of(c.getId(), b.getId(), a.getId(), d.getId()), ids(page(search().param("sort", "price,desc"))));
        assertEquals(List.of(d.getId(), b.getId(), c.getId(), a.getId()), ids(page(search().param("sort", "maxPrice,asc"))));
    }

    @Test void defaultAndWhitelistedSortsAreDeterministicAndExplicitMultipleSortsKeepTheirPriority() throws Exception {
        List<Product> data = sortFixture(); Product a = data.get(0), b = data.get(1), c = data.get(2), d = data.get(3);
        List<Long> reverseIds = List.of(d.getId(), c.getId(), b.getId(), a.getId());
        assertEquals(reverseIds, ids(page(search())));
        assertEquals(reverseIds, ids(page(search().param("sort", "createdAt,desc"))));
        assertEquals(reverseIds, ids(page(search().param("sort", "updatedAt,desc"))));
        assertEquals(List.of(a.getId(), b.getId(), d.getId(), c.getId()), ids(page(search().param("sort", "name,asc"))));
        assertEquals(List.of(a.getId(), b.getId(), c.getId(), d.getId()), ids(page(search().param("sort", "id,asc"))));
        assertEquals(List.of(a.getId(), c.getId(), b.getId(), d.getId()), ids(page(search().param("sort", "averageRating,desc"))));
        assertEquals(List.of(a.getId(), c.getId(), b.getId(), d.getId()), ids(page(search().param("sort", "totalSold,desc"))));
        assertEquals(List.of(a.getId(), b.getId(), c.getId(), d.getId()), ids(page(search().param("sort", "averageRating,desc", "name,asc"))));
    }

    @Test void zeroDiscountAndMaximumSupportedMoneyKeepExactInclusiveBounds() throws Exception {
        Product free = product("Free after catalog discount"); variant(free, "100.00", "0.00");
        Product maximum = product("Maximum price"); variant(maximum, "9999999999.99", null);
        product("No price SKU");
        exactly(page(search().param("minPrice", "0").param("maxPrice", "0.00").param("onSale", "true")), free);
        JsonNode result = page(search().param("minPrice", "9999999999.99").param("maxPrice", "9999999999.99"));
        exactly(result, maximum);
        assertEquals(0, new BigDecimal("9999999999.99").compareTo(result.path("content").get(0).path("minPrice").decimalValue()));
    }

    @Test void invalidMoneyIdsAndRawPaginationAreClientErrorsInsteadOfClampedOrServerFailures() throws Exception {
        List<String[]> invalid = List.of(new String[]{"page", "-1"}, new String[]{"page", "2147483648"}, new String[]{"size", "0"}, new String[]{"size", "101"},
                new String[]{"size", "2147483648"}, new String[]{"brandId", "0"}, new String[]{"categoryId", "-1"}, new String[]{"brandIds", "1,-2"},
                new String[]{"categoryIds", "0,2"}, new String[]{"tagIds", "-1"}, new String[]{"ingredientIds", "0"}, new String[]{"skinConcernIds", "-1"},
                new String[]{"brandIds", "abc"}, new String[]{"categoryIds", "1,,2"}, new String[]{"minPrice", "-0.01"}, new String[]{"maxPrice", "10000000000.00"},
                new String[]{"minPrice", "0.001"}, new String[]{"minPrice", "abc"}, new String[]{"minRating", "-0.1"}, new String[]{"minRating", "5.1"},
                new String[]{"keyword", "x".repeat(201)}, new String[]{"originCountry", "x".repeat(101)},
                new String[]{"brandIds", String.join(",", Collections.nCopies(51, "1"))});
        for (String[] invalidParameter : invalid) mvc.perform(get("/api/v1/products/search").param(invalidParameter[0], invalidParameter[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/v1/products/search").param("minPrice", "100").param("maxPrice", "99.99"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/search").param("page", "2147483647").param("size", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test void invalidEnumsBooleansAndSortFieldsAreClientErrorsAndStatusQueryCannotExposePrivateProducts() throws Exception {
        for (String[] invalid : List.of(new String[]{"productType", "PHYSICAL"}, new String[]{"skinType", "ALL"}, new String[]{"targetGender", "UNKNOWN"},
                new String[]{"inStock", "sometimes"}, new String[]{"onSale", "sometimes"}, new String[]{"isFeatured", "sometimes"},
                new String[]{"sort", "unknownField,asc"}, new String[]{"sort", "brand.name,asc"}, new String[]{"sort", "price;select,asc"}, new String[]{"sort", "id,banana"})) {
            mvc.perform(get("/api/v1/products/search").param(invalid[0], invalid[1])).andExpect(status().isBadRequest());
        }
        Product visible = product("Visible"); Product privateProduct = product("Private"); privateProduct.setStatus(ProductStatus.INACTIVE);
        exactly(page(search().param("status", "INACTIVE")), visible);
    }
}
