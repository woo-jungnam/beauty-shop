import '../storage/storage_service.dart';
import 'package:flutter/foundation.dart';

class ApiConstants {
  static String? _customBaseUrl;

  static void setCustomBaseUrl(String url) {
    _customBaseUrl = url;
    StorageService.setBaseUrl(url);
  }

  static String get baseUrl {
    if (_customBaseUrl != null && _customBaseUrl!.isNotEmpty) {
      return _customBaseUrl!;
    }
    final savedUrl = StorageService.getBaseUrl();
    if (savedUrl != null && savedUrl.isNotEmpty) {
      return savedUrl;
    }
    const envUrl = String.fromEnvironment('BACKEND_URL');
    if (envUrl.isNotEmpty) {
      return envUrl;
    }
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:8080';
    }
    return 'http://127.0.0.1:8080';
  }

  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 30);

  // Authentication
  static const String login = '/api/v1/auth/login';
  static const String register = '/api/v1/auth/register';
  static const String refreshToken = '/api/v1/auth/refresh';
  static const String logout = '/api/v1/auth/logout';
  static const String currentUserProfile = '/api/v1/auth/profile';
  static const String updateProfile = '/api/v1/users/profile';

  // Catalog & Products
  static const String products = '/api/v1/products';
  static const String productFeatured = '/api/v1/products/featured';
  static const String recommendedForYou = '/api/v1/products/recommended-for-you';
  static const String expiringSoon = '/api/v1/products/expiring-soon';
  static const String productSearch = '/api/v1/products/search';
  static const String productCategory = '/api/v1/products/category/'; // + categoryId
  static const String productDetail = '/api/v1/products/'; // + id
  static const String categories = '/api/v1/categories';
  static const String rootCategories = '/api/v1/categories/root';
  static const String brands = '/api/v1/brands';
  static const String banners = '/api/v1/banners';
  static const String reviews = '/api/v1/reviews';

  // Cart
  static const String cart = '/api/v1/cart';
  static const String addToCart = '/api/v1/cart/add';
  static const String updateCartItem = '/api/v1/cart/items/'; // + itemId
  static const String removeCartItem = '/api/v1/cart/items/'; // + itemId
  static const String clearCart = '/api/v1/cart/clear';
  static const String mergeCart = '/api/v1/cart/merge';

  // Orders & Checkout
  static const String checkout = '/api/v1/orders/checkout';
  static const String myOrders = '/api/v1/orders/my-orders';
  static const String orderDetail = '/api/v1/orders/'; // + id
  static const String cancelOrder = '/api/v1/orders/'; // + id + '/cancel'
  static String orderCancelUrl(int id) => '/api/v1/orders/$id/cancel';

  // Spa & Clinic
  static const String spaServices = '/api/v1/spa/services';
  static const String spaPackages = '/api/v1/spa/services/packages';
  static const String spaTickets = '/api/v1/spa/tickets/my-tickets';
  static const String spaActiveTickets = '/api/v1/spa/tickets/my-active-tickets';
  static const String bookAppointment = '/api/v1/appointments/book';
  static const String myAppointments = '/api/v1/appointments/my-appointments';
  static const String cancelAppointment = '/api/v1/appointments/'; // + id + '/cancel'
  static String appointmentCancelUrl(int id) => '/api/v1/appointments/$id/cancel';

  // Chatbot AI
  static const String chat = '/api/v1/chatbot/chat';
  static const String chatStream = '/api/v1/chatbot/chat/stream';

  // System & Public Configs
  static const String publicConfigs = '/api/v1/system-configs/public';
}
