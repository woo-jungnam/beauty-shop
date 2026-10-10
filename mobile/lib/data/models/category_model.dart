class CategoryItem {
  final int id;
  final String name;
  final String slug;
  final String? description;
  final String? iconUrl;
  final String? imageUrl;
  final int? parentId;
  final List<CategoryItem> children;

  String? get displayImage => (iconUrl != null && iconUrl!.isNotEmpty)
      ? iconUrl
      : (imageUrl != null && imageUrl!.isNotEmpty ? imageUrl : null);

  CategoryItem({
    required this.id,
    required this.name,
    required this.slug,
    this.description,
    this.iconUrl,
    this.imageUrl,
    this.parentId,
    this.children = const [],
  });

  factory CategoryItem.fromJson(Map<String, dynamic> json) {
    final rawChildren = json['children'] as List<dynamic>? ?? [];
    final img = json['imageUrl']?.toString() ??
        json['image_url']?.toString() ??
        json['iconUrl']?.toString() ??
        json['icon_url']?.toString() ??
        json['image']?.toString();
    return CategoryItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name']?.toString() ?? '',
      slug: json['slug']?.toString() ?? '',
      description: json['description']?.toString(),
      iconUrl: img,
      imageUrl: img,
      parentId: (json['parentId'] as num?)?.toInt(),
      children: rawChildren
          .map((c) => CategoryItem.fromJson(c as Map<String, dynamic>))
          .toList(),
    );
  }
}
