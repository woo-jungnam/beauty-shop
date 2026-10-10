class SpaService {
  final int id;
  final String name;
  final String slug;
  final String? category;
  final String? description;
  final double price;
  final double? originalPrice;
  final int durationMinutes;
  final String? thumbnailUrl;
  final List<String> steps;
  final bool isDoctorConsultation;

  SpaService({
    required this.id,
    required this.name,
    required this.slug,
    this.category,
    this.description,
    required this.price,
    this.originalPrice,
    this.durationMinutes = 60,
    this.thumbnailUrl,
    this.steps = const [],
    this.isDoctorConsultation = true,
  });

  factory SpaService.fromJson(Map<String, dynamic> json) {
    final priceVal = (json['price'] ?? json['basePrice']) as num?;
    return SpaService(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name']?.toString() ?? '',
      slug: json['slug']?.toString() ?? '',
      category: json['category']?.toString() ?? json['categoryName']?.toString() ?? 'Chăm sóc da',
      description: json['description']?.toString(),
      price: priceVal?.toDouble() ?? 0.0,
      originalPrice: (json['originalPrice'] as num?)?.toDouble(),
      durationMinutes: (json['durationMinutes'] ?? json['duration']) as int? ?? 60,
      thumbnailUrl: json['thumbnailUrl']?.toString() ?? json['image']?.toString(),
      steps: (json['steps'] as List<dynamic>?)?.map((s) => s.toString()).toList() ?? const [],
      isDoctorConsultation: json['isDoctorConsultation'] != false,
    );
  }
}

class Appointment {
  final int id;
  final int? serviceId;
  final String serviceName;
  final String branchName;
  final DateTime appointmentDate;
  final String timeSlot;
  final String status;
  final String? notes;

  Appointment({
    required this.id,
    this.serviceId,
    required this.serviceName,
    this.branchName = 'Chi nhánh Hasaki Quận 1',
    required this.appointmentDate,
    required this.timeSlot,
    required this.status,
    this.notes,
  });

  bool get canCancel => status == 'PENDING' || status == 'CONFIRMED';

  String get statusLabel => switch (status) {
    'PENDING' => 'Chờ xác nhận',
    'CONFIRMED' => 'Đã xác nhận',
    'IN_PROGRESS' => 'Đang thực hiện',
    'COMPLETED' => 'Hoàn thành',
    'CANCELLED' => 'Đã hủy',
    _ => status,
  };

  factory Appointment.fromJson(Map<String, dynamic> json) {
    final items = json['items'] as List<dynamic>? ?? [];
    String svcName = 'Dịch vụ Spa & Clinic';
    int? sId;
    if (items.isNotEmpty) {
      final first = items.first as Map<String, dynamic>;
      svcName = first['serviceName']?.toString() ?? svcName;
      sId = (first['serviceId'] as num?)?.toInt();
    } else if (json['serviceName'] != null) {
      svcName = json['serviceName'].toString();
      sId = (json['serviceId'] as num?)?.toInt();
    }

    final dateStr = json['appointmentDate']?.toString();
    final timeStr = json['startTime']?.toString() ?? json['timeSlot']?.toString() ?? '09:00';

    return Appointment(
      id: (json['id'] as num?)?.toInt() ?? 0,
      serviceId: sId,
      serviceName: svcName,
      branchName: json['branchName']?.toString() ?? 'Chi nhánh Hasaki Quận 1 (71 Hoàng Hoa Thám)',
      appointmentDate: dateStr != null ? DateTime.tryParse(dateStr) ?? DateTime.now() : DateTime.now(),
      timeSlot: timeStr,
      status: json['status']?.toString() ?? 'PENDING',
      notes: json['notes']?.toString(),
    );
  }
}

class UserServiceTicket {
  final int id;
  final String ticketCode;
  final String serviceName;
  final int remainingQuantity;
  final int totalQuantity;
  final String status;
  final String? expiresAt;
  final String? qrCodeUrl;

  UserServiceTicket({
    required this.id,
    required this.ticketCode,
    required this.serviceName,
    required this.remainingQuantity,
    required this.totalQuantity,
    required this.status,
    this.expiresAt,
    this.qrCodeUrl,
  });

  bool get isValid => status == 'ACTIVE' && remainingQuantity > 0;

  factory UserServiceTicket.fromJson(Map<String, dynamic> json) {
    return UserServiceTicket(
      id: (json['id'] as num?)?.toInt() ?? 0,
      ticketCode: json['ticketCode']?.toString() ?? 'TKT-${json['id']}',
      serviceName: json['serviceName']?.toString() ?? 'Liệu trình chăm sóc da',
      remainingQuantity: (json['remainingQuantity'] ?? json['remainingSessions']) as int? ?? 1,
      totalQuantity: (json['totalQuantity'] ?? json['totalSessions']) as int? ?? 1,
      status: json['status']?.toString() ?? 'ACTIVE',
      expiresAt: json['expiresAt']?.toString() ?? json['expiryDate']?.toString(),
      qrCodeUrl: json['qrCodeUrl']?.toString(),
    );
  }
}
