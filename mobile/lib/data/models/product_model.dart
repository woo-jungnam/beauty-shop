class ProductItem {
  final int id;
  final String name;
  final String slug;
  final String? shortDescription;
  final String? thumbnailUrl;
  final double basePrice;
  final double? salePrice;
  final String status;
  final bool isFeatured;
  final double averageRating;
  final int totalReviews;
  final int totalSold;
  final String? brandName;
  final ProductVariant? clearanceVariant;
  final DateTime? earliestExpirationDate;
  final int? daysRemaining;
  final int? clearanceStock;

  ProductItem({
    required this.id,
    required this.name,
    required this.slug,
    this.shortDescription,
    this.thumbnailUrl,
    required this.basePrice,
    this.salePrice,
    this.status = 'ACTIVE',
    this.isFeatured = false,
    this.averageRating = 0.0,
    this.totalReviews = 0,
    this.totalSold = 0,
    this.brandName,
    this.clearanceVariant,
    this.earliestExpirationDate,
    this.daysRemaining,
    this.clearanceStock,
  });

  int get discountPercent {
    if (salePrice != null &&
        salePrice! >= 0 &&
        salePrice! < basePrice &&
        basePrice > 0) {
      return ((basePrice - salePrice!) / basePrice * 100).round();
    }
    return 0;
  }

  double get displayPrice =>
      salePrice != null && salePrice! >= 0 && salePrice! < basePrice
      ? salePrice!
      : basePrice;

  bool get isAvailableClearance =>
      status == 'ACTIVE' &&
      clearanceVariant?.isAvailable == true &&
      clearanceVariant!.hasDiscount &&
      earliestExpirationDate != null &&
      daysRemaining != null &&
      (clearanceStock ?? 0) > 0;

  factory ProductItem.fromJson(Map<String, dynamic> json) {
    final clearance = json['clearanceVariant'] is Map<String, dynamic>
        ? ProductVariant.fromJson(
            json['clearanceVariant'] as Map<String, dynamic>,
          )
        : null;
    double? lowestVariantPrice;
    if (json['variants'] is List && (json['variants'] as List).isNotEmpty) {
      for (final v in json['variants']) {
        final vPrice = (v['discountPrice'] ?? v['price']) as num?;
        if (vPrice != null) {
          if (lowestVariantPrice == null ||
              vPrice.toDouble() < lowestVariantPrice) {
            lowestVariantPrice = vPrice.toDouble();
          }
        }
      }
    }

    final baseP =
        clearance?.price ??
        ((json['basePrice'] ?? json['minPrice'] ?? json['price']) as num?)
            ?.toDouble() ??
        0.0;
    final saleP = clearance != null
        ? clearance.discountPrice
        : (json['salePrice'] as num?)?.toDouble() ?? lowestVariantPrice;

    return ProductItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name']?.toString() ?? '',
      slug: json['slug']?.toString() ?? '',
      shortDescription: json['shortDescription']?.toString(),
      thumbnailUrl:
          json['thumbnailUrl']?.toString() ?? json['image']?.toString(),
      basePrice: baseP > 0 ? baseP : (saleP ?? 0.0),
      salePrice: saleP != null && saleP >= 0 && saleP < baseP ? saleP : null,
      status: json['status']?.toString() ?? 'ACTIVE',
      isFeatured: json['isFeatured'] == true,
      averageRating: (json['averageRating'] as num?)?.toDouble() ?? 0.0,
      totalReviews: (json['totalReviews'] as num?)?.toInt() ?? 0,
      totalSold: (json['totalSold'] as num?)?.toInt() ?? 0,
      brandName:
          json['brandName']?.toString() ??
          (json['brand'] is Map ? json['brand']['name']?.toString() : null),
      clearanceVariant: clearance,
      earliestExpirationDate: DateTime.tryParse(
        json['earliestExpirationDate']?.toString() ?? '',
      ),
      daysRemaining: (json['daysRemaining'] as num?)?.toInt(),
      clearanceStock: (json['clearanceStock'] as num?)?.toInt(),
    );
  }
}

class ProductVariant {
  final int id;
  final String sku;
  final String variantName;
  final double price;
  final double? originalPrice;
  final double? discountPrice;
  final int stockQuantity;
  final String? thumbnailUrl;
  final bool isDefault;
  final bool isActive;

  ProductVariant({
    required this.id,
    required this.sku,
    required this.variantName,
    required this.price,
    this.originalPrice,
    this.discountPrice,
    this.stockQuantity = 0,
    this.thumbnailUrl,
    this.isDefault = false,
    this.isActive = true,
  });

  bool get hasDiscount =>
      discountPrice != null && discountPrice! >= 0 && discountPrice! < price;

  bool get isAvailable => id > 0 && isActive && stockQuantity > 0;

  double get effectivePrice => hasDiscount ? discountPrice! : price;

  int get discountPercent {
    final orig = originalPrice ?? price;
    if (effectivePrice < orig && orig > 0) {
      return ((orig - effectivePrice) / orig * 100).round();
    }
    return 0;
  }

  factory ProductVariant.fromJson(Map<String, dynamic> json) {
    return ProductVariant(
      id: (json['id'] as num?)?.toInt() ?? 0,
      sku: json['sku']?.toString() ?? '',
      variantName: json['variantName']?.toString() ?? 'Mặc định',
      price: (json['price'] as num?)?.toDouble() ?? 0.0,
      originalPrice: (json['originalPrice'] as num?)?.toDouble(),
      discountPrice: (json['discountPrice'] as num?)?.toDouble(),
      stockQuantity: (json['stockQuantity'] as num?)?.toInt() ?? 0,
      thumbnailUrl: json['thumbnailUrl']?.toString(),
      isDefault: json['isDefault'] == true,
      isActive: json['isActive'] != false,
    );
  }
}

class ProductAttribute {
  final int id;
  final String name;
  final String value;
  final String dataType;
  final int? variantId;

  ProductAttribute.fromJson(Map<String, dynamic> json)
    : id = (json['id'] as num?)?.toInt() ?? 0,
      name = json['attributeDefinitionName']?.toString() ?? '',
      value = json['value']?.toString() ?? '',
      dataType = json['dataType']?.toString() ?? 'STRING',
      variantId = (json['productVariantId'] as num?)?.toInt();

  String get displayValue =>
      dataType == 'BOOLEAN' ? (value == 'true' ? 'Có' : 'Không') : value;
}

class ProductIngredient {
  final String name;
  final String inciName;
  final num? concentration;
  final String unit;
  final bool isKeyActive;
  final List<String> functions;
  final List<String> benefits;
  final List<String> concerns;

  ProductIngredient.fromJson(Map<String, dynamic> json)
    : name = json['name']?.toString() ?? '',
      inciName = json['inciName']?.toString() ?? '',
      concentration = json['concentration'] as num?,
      unit = json['concentrationUnit']?.toString() ?? '',
      isKeyActive = json['isKeyActive'] == true,
      functions = (json['function'] as List<dynamic>? ?? [])
          .map((item) => item.toString())
          .toList(),
      benefits = (json['benefits'] as List<dynamic>? ?? [])
          .map((item) => item.toString())
          .toList(),
      concerns = (json['potentialConcerns'] as List<dynamic>? ?? [])
          .map((item) => item.toString())
          .toList();

  String get concentrationLabel => concentration == null
      ? ''
      : '${concentration!.toString().replaceFirst(RegExp(r'\.0$'), '')}$unit';
}

class ProductDetail {
  final int id;
  final String name;
  final String slug;
  final String? shortDescription;
  final String? description;
  final String? thumbnailUrl;
  final double basePrice;
  final String status;
  final String? targetGender;
  final String? skinType;
  final String? ingredients;
  final String? keyActivesSummary;
  final List<ProductAttribute> attributeValues;
  final List<ProductIngredient> ingredientsList;
  final String? howToUse;
  final String? originCountry;
  final String? volume;
  final bool? hasFragrance;
  final bool? hasAlcohol;
  final double averageRating;
  final int totalReviews;
  final int totalSold;
  final String? brandName;
  final String? categoryName;
  final List<ProductVariant> variants;
  final List<String> imageUrls;
  final List<String> tags;

  ProductDetail({
    required this.id,
    required this.name,
    required this.slug,
    this.shortDescription,
    this.description,
    this.thumbnailUrl,
    required this.basePrice,
    this.status = 'ACTIVE',
    this.targetGender,
    this.skinType,
    this.ingredients,
    this.keyActivesSummary,
    this.attributeValues = const [],
    this.ingredientsList = const [],
    this.howToUse,
    this.originCountry,
    this.volume,
    this.hasFragrance,
    this.hasAlcohol,
    this.averageRating = 0.0,
    this.totalReviews = 0,
    this.totalSold = 0,
    this.brandName,
    this.categoryName,
    this.variants = const [],
    this.imageUrls = const [],
    this.tags = const [],
  });

  ProductVariant? variantForSelection(int? requestedId) {
    if (requestedId != null) {
      return variants
          .where((v) => v.id == requestedId && v.isActive)
          .firstOrNull;
    }
    return variants.where((v) => v.isAvailable && v.isDefault).firstOrNull ??
        variants.where((v) => v.isAvailable).firstOrNull ??
        variants.where((v) => v.isActive).firstOrNull;
  }

  factory ProductDetail.fromJson(Map<String, dynamic> json) {
    final variantsRaw = json['variants'] as List<dynamic>? ?? [];
    final variantsList = variantsRaw
        .map((v) => ProductVariant.fromJson(v as Map<String, dynamic>))
        .where((variant) => variant.isActive)
        .toList();
    variantsList.sort((a, b) => a.isDefault ? -1 : 1);

    final imagesRaw = json['images'] as List<dynamic>? ?? [];
    final imagesList = imagesRaw
        .map((img) => (img['imageUrl'] ?? img['url'] ?? '').toString())
        .where((s) => s.isNotEmpty)
        .toList();

    final thumb = json['thumbnailUrl']?.toString();
    if (imagesList.isEmpty && thumb != null && thumb.isNotEmpty) {
      imagesList.add(thumb);
    }

    return ProductDetail(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name']?.toString() ?? '',
      slug: json['slug']?.toString() ?? '',
      shortDescription: json['shortDescription']?.toString(),
      description: json['description']?.toString(),
      thumbnailUrl: thumb,
      basePrice: (json['basePrice'] as num?)?.toDouble() ?? 0.0,
      status: json['status']?.toString() ?? 'ACTIVE',
      targetGender: json['targetGender']?.toString(),
      skinType: json['skinType']?.toString(),
      ingredients: json['ingredients']?.toString(),
      keyActivesSummary: json['keyActivesSummary']?.toString(),
      attributeValues: (json['attributeValues'] as List<dynamic>? ?? [])
          .map(
            (item) => ProductAttribute.fromJson(item as Map<String, dynamic>),
          )
          .toList(),
      ingredientsList: (json['ingredientsList'] as List<dynamic>? ?? [])
          .map(
            (item) => ProductIngredient.fromJson(item as Map<String, dynamic>),
          )
          .toList(),
      howToUse: json['howToUse']?.toString(),
      originCountry: json['originCountry']?.toString(),
      volume: json['volume']?.toString(),
      hasFragrance: json['hasFragrance'] as bool?,
      hasAlcohol: json['hasAlcohol'] as bool?,
      averageRating: (json['averageRating'] as num?)?.toDouble() ?? 0.0,
      totalReviews: (json['totalReviews'] as num?)?.toInt() ?? 0,
      totalSold: (json['totalSold'] as num?)?.toInt() ?? 0,
      brandName:
          json['brandName']?.toString() ??
          (json['brand'] is Map ? json['brand']['name']?.toString() : null),
      categoryName:
          json['categoryName']?.toString() ??
          (json['category'] is Map
              ? json['category']['name']?.toString()
              : null),
      variants: variantsList,
      imageUrls: imagesList,
      tags:
          (json['tags'] as List<dynamic>?)?.map((t) => t.toString()).toList() ??
          const [],
    );
  }
}
