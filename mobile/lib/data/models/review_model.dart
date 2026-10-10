class ReviewItem {
  final int id;
  final int productId;
  final int? orderId;
  final String authorName;
  final int rating;
  final String? title;
  final String? content;
  final String status;
  final DateTime createdAt;

  ReviewItem({
    required this.id,
    required this.productId,
    this.orderId,
    required this.authorName,
    required this.rating,
    this.title,
    this.content,
    this.status = 'APPROVED',
    required this.createdAt,
  });

  factory ReviewItem.fromJson(Map<String, dynamic> json) {
    DateTime parsedDate = DateTime.now();
    if (json['createdAt'] != null) {
      parsedDate = DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now();
    }
    return ReviewItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      productId: (json['productId'] as num?)?.toInt() ?? 0,
      orderId: (json['orderId'] as num?)?.toInt(),
      authorName: json['authorName']?.toString() ?? json['userName']?.toString() ?? 'Khách hàng',
      rating: (json['rating'] as num?)?.toInt() ?? 5,
      title: json['title']?.toString(),
      content: json['content']?.toString() ?? json['comment']?.toString(),
      status: json['status']?.toString() ?? 'APPROVED',
      createdAt: parsedDate,
    );
  }
}

class CreateReviewRequest {
  final int productId;
  final int? orderId;
  final int? rating;
  final String title;
  final String content;

  CreateReviewRequest({
    required this.productId,
    this.orderId,
    this.rating,
    required this.title,
    required this.content,
  });

  Map<String, dynamic> toJson() => {
    'productId': productId,
    if (orderId != null) 'orderId': orderId,
    if (rating != null) 'rating': rating,
    'title': title,
    'content': content,
  };
}
