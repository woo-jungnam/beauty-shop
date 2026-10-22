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
  final ApiClient _apiClient;
  List<ProductItem> _products = [],
      _flashSaleProducts = [],
      _recommendedProducts = [],
      _bestSellingProducts = [];
  List<CategoryItem> _categories = [];
  List<BrandItem> _brands = [];
  List<BannerItem> _banners = [];
  bool _isLoading = false, _isLoadingDetail = false;
  String? _errorMessage,
      _categoriesError,
      _dealsError,
      _recommendedError,
      _bestSellingError,
      _detailError;
  ProductDetail? _selectedProductDetail;
  List<ReviewItem> _currentProductReviews = [];
  List<ProductItem> _similarProducts = [];
  final Map<int, List<ProductItem>> _categoryProductsMap = {};
  final Map<int, String> _categoryErrors = {};
  final Set<int> _loadingCategories = {};

  List<ProductItem> get products => _products;
  List<ProductItem> get flashSaleProducts => _flashSaleProducts;
  List<ProductItem> get recommendedProducts => _recommendedProducts;
  List<ProductItem> get bestSellingProducts => _bestSellingProducts;
  List<CategoryItem> get categories => _categories;
  List<BrandItem> get brands => _brands;
  List<BannerItem> get banners => _banners;
  bool get isLoading => _isLoading;
  bool get isLoadingDetail => _isLoadingDetail;
  String? get errorMessage => _errorMessage;
  String? get categoriesError => _categoriesError;
  String? get dealsError => _dealsError;
  String? get recommendedError => _recommendedError;
  String? get bestSellingError => _bestSellingError;
  String? get detailError => _detailError;
  String? _reviewsError;
  String? get reviewsError => _reviewsError;
  ProductDetail? get selectedProductDetail => _selectedProductDetail;
  List<ReviewItem> get currentProductReviews => _currentProductReviews;
  List<ProductItem> get similarProducts => _similarProducts;
  Map<int, List<ProductItem>> get categoryProductsMap => _categoryProductsMap;
  bool get isLoadingCategoryProducts => _loadingCategories.isNotEmpty;
  bool isLoadingCategory(int id) => _loadingCategories.contains(id);
  String? categoryError(int id) => _categoryErrors[id];

  ProductProvider({ApiClient? apiClient, bool loadOnCreate = true})
    : _apiClient = apiClient ?? ApiClient() {
    if (loadOnCreate) loadInitialData();
  }

  Future<void> loadInitialData() async {
    _isLoading = true;
    _categoryProductsMap.clear();
    _categoryErrors.clear();
    notifyListeners();
    await Future.wait([
      fetchProducts(),
      fetchCategories(),
      fetchBrands(),
      fetchBanners(),
      fetchRecommendedProducts(),
      fetchFlashSaleProducts(),
      fetchBestSellingProducts(),
    ]);
    _isLoading = false;
    notifyListeners();
  }

  Future<List<ProductItem>> fetchCatalogProducts({
    Map<String, dynamic> filters = const {},
  }) async {
    final response = await _apiClient.get<PageResponse<ProductItem>>(
      ApiConstants.productSearch,
      queryParameters: {
        'page': 0,
        'size': 60,
        'sort': 'createdAt,desc',
        ...filters,
      },
      fromJsonT: (json) => PageResponse.fromJson(
        json as Map<String, dynamic>,
        (item) => ProductItem.fromJson(item as Map<String, dynamic>),
      ),
    );
    if (!response.isSuccess || response.data == null)
      throw StateError(response.message ?? 'Không thể tải sản phẩm');
    return response.data!.content;
  }

  Future<void> fetchProducts({int page = 0, int size = 60}) async {
    _errorMessage = null;
    try {
      _products = await fetchCatalogProducts(
        filters: {'page': page, 'size': size},
      );
    } catch (_) {
      _products = [];
      _errorMessage = 'Không thể tải sản phẩm. Vui lòng thử lại.';
    }
    notifyListeners();
  }

  Future<void> fetchBestSellingProducts() async {
    _bestSellingError = null;
    try {
      _bestSellingProducts = await fetchCatalogProducts(
        filters: {'sort': 'totalSold,desc', 'size': 12},
      );
      _bestSellingProducts = _bestSellingProducts
          .where((product) => product.totalSold > 0)
          .toList();
    } catch (_) {
      _bestSellingProducts = [];
      _bestSellingError = 'Không thể tải sản phẩm bán chạy.';
    }
    notifyListeners();
  }

  Future<void> fetchFlashSaleProducts() async {
    _dealsError = null;
    try {
      final response = await _apiClient.get<List<ProductItem>>(
        ApiConstants.expiringSoon,
        queryParameters: {'thresholdDays': 90, 'limit': 50},
        fromJsonT: (json) => (json as List<dynamic>)
            .map((item) => ProductItem.fromJson(item as Map<String, dynamic>))
            .toList(),
      );
      if (!response.isSuccess || response.data == null) {
        throw StateError('Missing clearance products');
      }
      _flashSaleProducts = response.data!
          .where((item) => item.isAvailableClearance)
          .toList();
    } catch (_) {
      _flashSaleProducts = [];
      _dealsError = 'Không thể tải sản phẩm ưu đãi.';
    }
    notifyListeners();
  }

  Future<void> fetchRecommendedProducts() async {
    _recommendedError = null;
    try {
      final response = await _apiClient.get<List<ProductItem>>(
        ApiConstants.recommendedForYou,
        queryParameters: {'limit': 12},
        fromJsonT: (json) => (json as List<dynamic>)
            .map((item) => ProductItem.fromJson(item as Map<String, dynamic>))
            .toList(),
      );
      if (!response.isSuccess || response.data == null)
        throw StateError('Missing recommendations');
      _recommendedProducts = response.data!;
    } catch (_) {
      _recommendedProducts = [];
      _recommendedError = 'Không thể tải gợi ý sản phẩm.';
    }
    notifyListeners();
  }

  Future<void> fetchCategories() async {
    _categoriesError = null;
    try {
      final response = await _apiClient.get<List<CategoryItem>>(
        ApiConstants.categories,
        fromJsonT: (json) => (json as List<dynamic>)
            .map((item) => CategoryItem.fromJson(item as Map<String, dynamic>))
            .toList(),
      );
      if (!response.isSuccess || response.data == null)
        throw StateError('Missing categories');
      _categories = response.data!;
    } catch (_) {
      _categories = [];
      _categoriesError = 'Không thể tải danh mục.';
    }
    notifyListeners();
  }

  Future<void> fetchBrands() async {
    try {
      final response = await _apiClient.get<List<BrandItem>>(
        ApiConstants.brands,
        queryParameters: {'size': 20},
        fromJsonT: (json) {
          final list = json is Map
              ? json['content'] as List<dynamic>
              : json as List<dynamic>;
          return list
              .map((item) => BrandItem.fromJson(item as Map<String, dynamic>))
              .toList();
        },
      );
      _brands = response.isSuccess ? response.data ?? [] : [];
    } catch (_) {
      _brands = [];
    }
    notifyListeners();
  }

  Future<void> fetchBanners() async {
    try {
      final response = await _apiClient.get<List<BannerItem>>(
        ApiConstants.banners,
        fromJsonT: (json) => (json as List<dynamic>)
            .map((item) => BannerItem.fromJson(item as Map<String, dynamic>))
            .toList(),
      );
      _banners = response.isSuccess ? response.data ?? [] : [];
    } catch (_) {
      _banners = [];
    }
    notifyListeners();
  }

  Future<List<ProductItem>> fetchProductsByCategory(
    int categoryId, {
    bool refresh = false,
  }) async {
    if (!refresh && _categoryProductsMap.containsKey(categoryId))
      return _categoryProductsMap[categoryId]!;
    if (_loadingCategories.contains(categoryId))
      return _categoryProductsMap[categoryId] ?? [];
    _loadingCategories.add(categoryId);
    _categoryErrors.remove(categoryId);
    notifyListeners();
    try {
      _categoryProductsMap[categoryId] = await fetchCatalogProducts(
        filters: {'categoryId': categoryId},
      );
    } catch (_) {
      _categoryProductsMap.remove(categoryId);
      _categoryErrors[categoryId] =
          'Không thể tải sản phẩm trong danh mục này.';
    } finally {
      _loadingCategories.remove(categoryId);
      notifyListeners();
    }
    return _categoryProductsMap[categoryId] ?? [];
  }

  Future<List<ProductItem>> searchProductsApi(String keyword) =>
      fetchCatalogProducts(filters: {'keyword': keyword.trim()});

  Future<ProductDetail?> fetchProductDetail(int id) async {
    _isLoadingDetail = true;
    _detailError = null;
    _selectedProductDetail = null;
    _currentProductReviews = [];
    _similarProducts = [];
    notifyListeners();
    try {
      final response = await _apiClient.get<ProductDetail>(
        '${ApiConstants.productDetail}$id',
        fromJsonT: (json) =>
            ProductDetail.fromJson(json as Map<String, dynamic>),
      );
      if (!response.isSuccess || response.data == null)
        throw StateError('Missing product');
      _selectedProductDetail = response.data;
      await Future.wait([fetchReviewsForProduct(id), fetchSimilarProducts(id)]);
    } catch (_) {
      _detailError = 'Không thể tải chi tiết sản phẩm.';
    } finally {
      _isLoadingDetail = false;
      notifyListeners();
    }
    return _selectedProductDetail;
  }

  Future<void> fetchReviewsForProduct(int productId) async {
    _reviewsError = null;
    try {
      final response = await _apiClient.get<PageResponse<ReviewItem>>(
        ApiConstants.reviews,
        queryParameters: {
          'productId': productId,
          'size': 20,
          'sort': 'createdAt,desc',
        },
        fromJsonT: (json) => PageResponse.fromJson(
          json as Map<String, dynamic>,
          (item) => ReviewItem.fromJson(item as Map<String, dynamic>),
        ),
      );
      if (!response.isSuccess || response.data == null)
        throw StateError('Missing reviews');
      _currentProductReviews = response.data!.content;
    } catch (_) {
      _currentProductReviews = [];
      _reviewsError = 'Không thể tải đánh giá sản phẩm.';
    }
    notifyListeners();
  }

  Future<bool> submitReview(CreateReviewRequest request) async {
    try {
      final response = await _apiClient.post(
        ApiConstants.reviews,
        data: request.toJson(),
      );
      if (response.isSuccess) {
        await fetchReviewsForProduct(request.productId);
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
          final list = json is Map
              ? json['content'] as List<dynamic>
              : json as List<dynamic>;
          return list
              .map((item) => ProductItem.fromJson(item as Map<String, dynamic>))
              .toList();
        },
      );
      _similarProducts = response.isSuccess
          ? (response.data ?? [])
                .where((product) => product.id != productId)
                .toList()
          : [];
    } catch (_) {
      _similarProducts = [];
    }
    notifyListeners();
  }
}
