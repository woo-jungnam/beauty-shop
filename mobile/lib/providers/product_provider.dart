import 'package:flutter/material.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../core/network/api_response.dart';
import '../data/models/brand_model.dart';
import '../data/models/category_model.dart';
import '../data/models/product_model.dart';
import '../data/models/banner_model.dart';
import '../data/models/review_model.dart';

class ProductProvider extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();

  List<ProductItem> _products = [];
  List<ProductItem> _flashSaleProducts = [];
  List<ProductItem> _recommendedProducts = [];
  List<CategoryItem> _categories = [];
  List<BrandItem> _brands = [];
  List<BannerItem> _banners = [];

  bool _isLoading = false;
  bool _isLoadingDetail = false;
  String? _errorMessage;
  ProductDetail? _selectedProductDetail;
  List<ReviewItem> _currentProductReviews = [];
  List<ProductItem> _similarProducts = [];

  List<ProductItem> get products => _products;
  List<ProductItem> get flashSaleProducts => _flashSaleProducts;
  List<ProductItem> get recommendedProducts => _recommendedProducts;
  List<CategoryItem> get categories => _categories;
  List<BrandItem> get brands => _brands;
  List<BannerItem> get banners => _banners;
  bool get isLoading => _isLoading;
  bool get isLoadingDetail => _isLoadingDetail;
  String? get errorMessage => _errorMessage;
  ProductDetail? get selectedProductDetail => _selectedProductDetail;
  List<ReviewItem> get currentProductReviews => _currentProductReviews;
  List<ProductItem> get similarProducts => _similarProducts;

  ProductProvider() {
    loadInitialData();
  }

  Future<void> loadInitialData() async {
    _isLoading = true;
    notifyListeners();
    await Future.wait([
      fetchProducts(),
      fetchCategories(),
      fetchBrands(),
      fetchBanners(),
    ]);
    _isLoading = false;
    notifyListeners();
  }

  Future<void> fetchBanners() async {
    try {
      final response = await _apiClient.get<List<BannerItem>>(
        ApiConstants.banners,
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list.map((e) => BannerItem.fromJson(e as Map<String, dynamic>)).toList();
        },
      );
      if (response.isSuccess && response.data != null) {
        _banners = response.data!;
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<void> fetchProducts({int page = 0, int size = 60}) async {
    try {
      final response = await _apiClient.get<PageResponse<ProductItem>>(
        ApiConstants.products,
        queryParameters: {'page': page, 'size': size, 'sort': 'id,desc'},
        fromJsonT: (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          (item) => ProductItem.fromJson(item as Map<String, dynamic>),
        ),
      );

      if (response.isSuccess && response.data != null) {
        _products = response.data!.content;
        fetchRecommendedProducts();
        fetchFlashSaleProducts();
        notifyListeners();
      }
    } catch (e) {
      _errorMessage = 'Không thể tải danh sách sản phẩm';
    }
  }

  Future<void> fetchRecommendedProducts() async {
    try {
      final response = await _apiClient.get<List<ProductItem>>(
        ApiConstants.recommendedForYou,
        queryParameters: {'limit': 12},
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list.map((e) => ProductItem.fromJson(e as Map<String, dynamic>)).toList();
        },
      );
      if (response.isSuccess && response.data != null && response.data!.isNotEmpty) {
        _recommendedProducts = response.data!;
        notifyListeners();
      } else {
        _recommendedProducts = _products.reversed.toList();
      }
    } catch (_) {
      _recommendedProducts = _products.reversed.toList();
    }
  }

  Future<void> fetchFlashSaleProducts() async {
    try {
      final response = await _apiClient.get<List<ProductItem>>(
        ApiConstants.expiringSoon,
        queryParameters: {'limit': 10, 'thresholdDays': 90},
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list.map((e) => ProductItem.fromJson(e as Map<String, dynamic>)).toList();
        },
      );
      if (response.isSuccess && response.data != null && response.data!.isNotEmpty) {
        _flashSaleProducts = response.data!;
        notifyListeners();
      } else {
        _flashSaleProducts = _products.where((p) => p.discountPercent > 0 || p.isFeatured).toList();
      }
    } catch (_) {
      _flashSaleProducts = _products.where((p) => p.discountPercent > 0 || p.isFeatured).toList();
    }
  }

  Future<void> fetchCategories() async {
    try {
      final response = await _apiClient.get<List<CategoryItem>>(
        ApiConstants.categories,
        fromJsonT: (json) {
          final list = json as List<dynamic>? ?? [];
          return list.map((e) => CategoryItem.fromJson(e as Map<String, dynamic>)).toList();
        },
      );
      if (response.isSuccess && response.data != null) {
        _categories = response.data!;
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<void> fetchBrands() async {
    try {
      final response = await _apiClient.get<List<BrandItem>>(
        ApiConstants.brands,
        queryParameters: {'size': 20},
        fromJsonT: (json) {
          final list = json is Map && json['content'] != null ? json['content'] as List : (json as List? ?? []);
          return list.map((e) => BrandItem.fromJson(e as Map<String, dynamic>)).toList();
        },
      );
      if (response.isSuccess && response.data != null) {
        _brands = response.data!;
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<ProductDetail?> fetchProductDetail(int id) async {
    _isLoadingDetail = true;
    _selectedProductDetail = null;
    _currentProductReviews = [];
    _similarProducts = [];
    notifyListeners();

    try {
      final response = await _apiClient.get<ProductDetail>(
        '${ApiConstants.productDetail}$id',
        fromJsonT: (json) => ProductDetail.fromJson(json as Map<String, dynamic>),
      );

      if (response.isSuccess && response.data != null) {
        _selectedProductDetail = response.data;
        // Fetch reviews & similar products in parallel
        await Future.wait([
          fetchReviewsForProduct(id),
          fetchSimilarProducts(id),
        ]);
      }
    } catch (e) {
      _selectedProductDetail = null;
    } finally {
      _isLoadingDetail = false;
      notifyListeners();
    }
    return _selectedProductDetail;
  }

  Future<void> fetchReviewsForProduct(int productId) async {
    try {
      final response = await _apiClient.get<PageResponse<ReviewItem>>(
        ApiConstants.reviews,
        queryParameters: {'productId': productId, 'size': 20, 'sort': 'createdAt,desc'},
        fromJsonT: (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          (item) => ReviewItem.fromJson(item as Map<String, dynamic>),
        ),
      );
      if (response.isSuccess && response.data != null) {
        _currentProductReviews = response.data!.content;
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<bool> submitReview(CreateReviewRequest req) async {
    try {
      final response = await _apiClient.post(
        ApiConstants.reviews,
        data: req.toJson(),
      );
      if (response.isSuccess) {
        await fetchReviewsForProduct(req.productId);
        return true;
      }
    } catch (_) {}
    return false;
  }

  Future<void> fetchSimilarProducts(int productId) async {
    try {
      final response = await _apiClient.get<List<ProductItem>>(
        '${ApiConstants.productDetail}$productId/similar',
        queryParameters: {'limit': 8},
        fromJsonT: (json) {
          final list = json is Map && json['content'] != null ? json['content'] as List : (json as List? ?? []);
          return list.map((e) => ProductItem.fromJson(e as Map<String, dynamic>)).toList();
        },
      );
      if (response.isSuccess && response.data != null) {
        _similarProducts = response.data!.where((p) => p.id != productId).toList();
      } else {
        // Fallback: pick other products
        _similarProducts = _products.where((p) => p.id != productId).take(6).toList();
      }
      notifyListeners();
    } catch (_) {
      _similarProducts = _products.where((p) => p.id != productId).take(6).toList();
      notifyListeners();
    }
  }

  final Map<int, List<ProductItem>> _categoryProductsMap = {};
  bool _isLoadingCategoryProducts = false;

  Map<int, List<ProductItem>> get categoryProductsMap => _categoryProductsMap;
  bool get isLoadingCategoryProducts => _isLoadingCategoryProducts;

  Future<List<ProductItem>> fetchProductsByCategory(int categoryId) async {
    if (_categoryProductsMap.containsKey(categoryId) && _categoryProductsMap[categoryId]!.isNotEmpty) {
      return _categoryProductsMap[categoryId]!;
    }
    _isLoadingCategoryProducts = true;
    notifyListeners();

    try {
      final response = await _apiClient.get<PageResponse<ProductItem>>(
        '${ApiConstants.productCategory}$categoryId',
        queryParameters: {'size': 60, 'sort': 'id,desc'},
        fromJsonT: (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          (item) => ProductItem.fromJson(item as Map<String, dynamic>),
        ),
      );
      if (response.isSuccess && response.data != null) {
        _categoryProductsMap[categoryId] = response.data!.content;
      } else {
        // Fallback filter
        _categoryProductsMap[categoryId] = _products.where((p) => p.id % 2 == 0).toList();
      }
    } catch (_) {
      _categoryProductsMap[categoryId] = _products.where((p) => p.id % 2 == 0).toList();
    } finally {
      _isLoadingCategoryProducts = false;
      notifyListeners();
    }
    return _categoryProductsMap[categoryId] ?? [];
  }

  Future<List<ProductItem>> searchProductsApi(String keyword) async {
    final cleanKeyword = keyword.trim();
    if (cleanKeyword.isEmpty) return _products;

    try {
      final response = await _apiClient.get<PageResponse<ProductItem>>(
        ApiConstants.productSearch,
        queryParameters: {'keyword': cleanKeyword, 'size': 60},
        fromJsonT: (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          (item) => ProductItem.fromJson(item as Map<String, dynamic>),
        ),
      );
      if (response.isSuccess && response.data != null) {
        return response.data!.content;
      }
    } catch (_) {}
    return searchProducts(cleanKeyword);
  }

  List<ProductItem> filterByActiveIngredient(String ingredient) {
    final query = ingredient.toLowerCase();
    return _products.where((p) {
      return p.name.toLowerCase().contains(query) ||
          (p.shortDescription != null && p.shortDescription!.toLowerCase().contains(query));
    }).toList();
  }

  List<ProductItem> searchProducts(String query, {int? categoryId, int? brandId}) {
    return _products.where((p) {
      final matchesQuery = query.isEmpty ||
          p.name.toLowerCase().contains(query.toLowerCase()) ||
          (p.brandName != null && p.brandName!.toLowerCase().contains(query.toLowerCase()));
      return matchesQuery;
    }).toList();
  }
}
