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
  final bool? isDoctorConsultation;
  final String serviceType;
  bool get isTreatmentCourse => serviceType == 'TREATMENT_COURSE';

  SpaService({
    required this.id,
    required this.name,
    required this.slug,
    this.category,
    this.description,
    required this.price,
    this.originalPrice,
    this.durationMinutes = 0,
    this.thumbnailUrl,
    this.steps = const [],
    this.isDoctorConsultation,
    this.serviceType = 'SINGLE_SESSION',
  });

  factory SpaService.fromJson(Map<String, dynamic> json) {
    final priceVal = (json['price'] ?? json['basePrice']) as num?;
    return SpaService(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name']?.toString() ?? '',
      slug: json['slug']?.toString() ?? '',
      category:
          json['category']?.toString() ?? json['categoryName']?.toString(),
      description: json['description']?.toString(),
      price: priceVal?.toDouble() ?? 0.0,
      originalPrice: (json['originalPrice'] as num?)?.toDouble(),
      durationMinutes:
          ((json['durationMinutes'] ?? json['duration']) as num?)?.toInt() ?? 0,
      thumbnailUrl:
          json['thumbnailUrl']?.toString() ?? json['image']?.toString(),
      steps:
          (json['steps'] as List<dynamic>?)
              ?.map((s) => s.toString())
              .toList() ??
          const [],
      isDoctorConsultation: json['isDoctorConsultation'] as bool?,
      serviceType: json['serviceType']?.toString() ?? 'SINGLE_SESSION',
    );
  }
}

class Appointment {
  final int id;
  final int? serviceId;
  final String serviceName;
  final String branchName;
  final DateTime? appointmentDate;
  final String timeSlot;
  final String status;
  final String? notes;
  final int? userId;
  final List<AppointmentItem> items;

  Appointment({
    required this.id,
    this.serviceId,
    required this.serviceName,
    this.branchName = '',
    required this.appointmentDate,
    required this.timeSlot,
    required this.status,
    this.notes,
    this.userId,
    this.items = const [],
  });

  bool get canCancel {
    if (status != 'PENDING' && status != 'CONFIRMED') return false;
    final date = appointmentDate;
    if (date == null || timeSlot.isEmpty) return false;
    final start = DateTime.tryParse(
      '${date.toIso8601String().substring(0, 10)}T$timeSlot',
    );
    return start != null && start.isAfter(DateTime.now());
  }

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
      svcName = items
          .map((item) => (item as Map)['serviceName']?.toString() ?? '')
          .where((name) => name.isNotEmpty)
          .join(', ');
      sId = (first['serviceId'] as num?)?.toInt();
    } else if (json['serviceName'] != null) {
      svcName = json['serviceName'].toString();
      sId = (json['serviceId'] as num?)?.toInt();
    }

    final dateStr = json['appointmentDate']?.toString();
    final timeStr =
        json['startTime']?.toString() ?? json['timeSlot']?.toString() ?? '';

    return Appointment(
      id: (json['id'] as num?)?.toInt() ?? 0,
      serviceId: sId,
      serviceName: svcName,
      branchName: json['branchName']?.toString() ?? '',
      appointmentDate: dateStr != null ? DateTime.tryParse(dateStr) : null,
      timeSlot: timeStr,
      status: json['status']?.toString() ?? 'UNKNOWN',
      notes: json['notes']?.toString(),
      userId: (json['userId'] as num?)?.toInt(),
      items: items
          .map((item) => AppointmentItem.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }
}

class AppointmentItem {
  final int id;
  final int? serviceId, staffId, treatmentSessionItemId;
  final String serviceName, executionStatus;
  final String? ticketUsageState;
  AppointmentItem({
    required this.id,
    this.serviceId,
    this.staffId,
    this.treatmentSessionItemId,
    required this.serviceName,
    required this.executionStatus,
    this.ticketUsageState,
  });
  factory AppointmentItem.fromJson(Map<String, dynamic> json) =>
      AppointmentItem(
        id: (json['id'] as num?)?.toInt() ?? 0,
        serviceId: (json['serviceId'] as num?)?.toInt(),
        staffId: (json['staffId'] as num?)?.toInt(),
        treatmentSessionItemId: (json['treatmentSessionItemId'] as num?)
            ?.toInt(),
        serviceName: json['serviceName']?.toString() ?? '',
        executionStatus: json['executionStatus']?.toString() ?? 'PLANNED',
        ticketUsageState: json['ticketUsageState']?.toString(),
      );
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
  final String benefitType;
  final int? treatmentCourseId;
  final bool nameSnapshot;
  final int reservedSessions;
  final Map<int, int> remainingByService;
  final List<Map<String, dynamic>> services;

  UserServiceTicket({
    required this.id,
    required this.ticketCode,
    required this.serviceName,
    required this.remainingQuantity,
    required this.totalQuantity,
    required this.status,
    this.expiresAt,
    this.qrCodeUrl,
    this.benefitType = 'SERVICE_PACKAGE',
    this.treatmentCourseId,
    this.nameSnapshot = false,
    this.reservedSessions = 0,
    this.remainingByService = const {},
    this.services = const [],
  });

  bool get isValid {
    final expiry = expiresAt == null ? null : DateTime.tryParse(expiresAt!);
    return status == 'ACTIVE' &&
        remainingQuantity > 0 &&
        (expiresAt == null ||
            (expiry != null && expiry.isAfter(DateTime.now())));
  }

  factory UserServiceTicket.fromJson(Map<String, dynamic> json) {
    return UserServiceTicket(
      id: (json['id'] as num?)?.toInt() ?? 0,
      ticketCode: json['ticketCode']?.toString() ?? '',
      serviceName:
          json['serviceName']?.toString() ??
          json['packageName']?.toString() ??
          'Chưa cập nhật tên dịch vụ',
      remainingQuantity:
          ((json['remainingQuantity'] ?? json['remainingSessions']) as num?)
              ?.toInt() ??
          0,
      totalQuantity:
          ((json['totalQuantity'] ?? json['totalSessions']) as num?)?.toInt() ??
          0,
      status: json['status']?.toString() ?? 'UNKNOWN',
      expiresAt:
          json['expiresAt']?.toString() ?? json['expiryDate']?.toString(),
      qrCodeUrl: json['qrCodeUrl']?.toString(),
      benefitType: json['benefitType']?.toString() ?? 'SERVICE_PACKAGE',
      treatmentCourseId: (json['treatmentCourseId'] as num?)?.toInt(),
      nameSnapshot: json['nameSnapshot'] == true,
      reservedSessions: (json['reservedSessions'] as num?)?.toInt() ?? 0,
      remainingByService: (json['remainingByService'] as Map? ?? {}).map(
        (k, v) => MapEntry(int.parse('$k'), (v as num).toInt()),
      ),
      services: (json['services'] as List? ?? [])
          .map((s) => Map<String, dynamic>.from(s as Map))
          .toList(),
    );
  }
}
