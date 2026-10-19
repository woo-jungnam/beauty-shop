typedef JsonMap = Map<String, dynamic>;

JsonMap treatmentMap(dynamic value) =>
    value is Map ? Map<String, dynamic>.from(value) : <String, dynamic>{};
List<JsonMap> treatmentMaps(dynamic value) => value is List
    ? value.whereType<Map>().map(treatmentMap).toList()
    : <JsonMap>[];
int treatmentInt(dynamic value) =>
    value is num ? value.toInt() : int.tryParse(value?.toString() ?? '') ?? 0;

String treatmentStatus(String value) =>
    const {
      'DRAFT': 'Bản nháp',
      'PUBLISHED': 'Đang bán',
      'ARCHIVED': 'Ngừng bán',
      'PENDING_INTAKE': 'Chờ tiếp nhận',
      'ACTIVE': 'Đang điều trị',
      'PAUSED': 'Tạm dừng',
      'PENDING_REVIEW': 'Chờ đánh giá',
      'COMPLETED': 'Hoàn thành',
      'CANCELLED': 'Đã dừng',
      'EXPIRED': 'Hết hạn',
      'PLANNED': 'Chờ đặt',
      'BOOKED': 'Đã đặt lịch',
      'IN_PROGRESS': 'Đang thực hiện',
      'PERFORMED': 'Đã thực hiện',
      'SKIPPED': 'Đã bỏ qua',
      'NO_SHOW': 'Vắng hẹn',
      'PENDING_APPROVAL': 'Chờ duyệt',
      'PENDING_CUSTOMER_ACCEPTANCE': 'Chờ khách xác nhận',
      'PENDING_PAYMENT': 'Chờ thanh toán',
      'APPLIED': 'Đã áp dụng',
      'ACCEPTED': 'Đã đồng ý',
      'APPROVED': 'Đã duyệt',
      'REJECTED': 'Đã từ chối',
      'PAUSE': 'Tạm dừng',
      'RESUME': 'Tiếp tục',
      'CANCEL': 'Dừng lộ trình',
      'EXTEND': 'Gia hạn',
      'REFUND': 'Hoàn tiền',
      'SCHEDULED': 'Đã đặt lịch',
      'AWAITING_RESULT': 'Chờ ghi kết quả',
      'AWAITING_CUSTOMER': 'Chờ khách xác nhận',
      'AWAITING_SETTLEMENT': 'Chờ thanh toán chênh lệch',
      'AWAITING_REFUND': 'Chờ xác nhận hoàn tiền',
      'REFUNDED': 'Đã hoàn tiền',
      'INCIDENT': 'Sự cố cần đánh giá',
      'WAIVED': 'Đã được miễn buổi',
      'PRICE_ADJUSTMENT': 'Hoàn chênh lệch kế hoạch',
    }[value] ??
    value;

class TreatmentTemplate {
  final JsonMap raw;
  TreatmentTemplate.fromJson(this.raw);
  int get id => treatmentInt(raw['id']);
  int get versionId => treatmentInt(raw['versionId']);
  int get versionNumber => treatmentInt(raw['versionNumber']);
  String get name => raw['name']?.toString() ?? '';
  String get description => raw['description']?.toString() ?? '';
  String get status => raw['status']?.toString() ?? '';
  String get terms => raw['terms']?.toString() ?? '';
  double get price => double.tryParse('${raw['price']}') ?? 0;
  int? get validityDays =>
      raw['validityDays'] == null ? null : treatmentInt(raw['validityDays']);
  List<JsonMap> get sessions => treatmentMaps(raw['sessions']);
  List<String> get allowedActions => (raw['allowedActions'] as List? ?? [])
      .map((value) => value.toString())
      .toList();
  JsonMap get command => {
    'name': name,
    'description': description,
    if (raw['serviceId'] != null) 'serviceId': raw['serviceId'],
    if (raw['packageId'] != null) 'packageId': raw['packageId'],
    'price': price,
    'validityDays': validityDays,
    'terms': terms,
    'sessions': sessions
        .map(
          (session) => {
            'sequence': session['sequence'],
            'title': session['title'],
            'minGapDays': session['minGapDays'] ?? 0,
            'maxGapDays': session['maxGapDays'],
            'required': session['required'] != false,
            'items': treatmentMaps(session['items'])
                .map(
                  (item) => {
                    'serviceId': item['serviceId'],
                    'required': item['required'] != false,
                  },
                )
                .toList(),
          },
        )
        .toList(),
  };
}

class TreatmentSessionItem {
  final JsonMap raw;
  TreatmentSessionItem.fromJson(this.raw);
  int get id => treatmentInt(raw['id']);
  int get serviceId => treatmentInt(raw['serviceId']);
  int? get appointmentId =>
      raw['appointmentId'] == null ? null : treatmentInt(raw['appointmentId']);
  int? get appointmentItemId => raw['appointmentItemId'] == null
      ? null
      : treatmentInt(raw['appointmentItemId']);
  String get name => raw['serviceName']?.toString() ?? '';
  String get status => raw['status']?.toString() ?? 'PLANNED';
  String get result => raw['result']?.toString() ?? '';
  String get aftercare => raw['aftercare']?.toString() ?? '';
  bool get required => raw['required'] != false;
}

class TreatmentSession {
  final JsonMap raw;
  TreatmentSession.fromJson(this.raw);
  int get id => treatmentInt(raw['id']);
  int get sequence => treatmentInt(raw['sequence']);
  String get title => raw['title']?.toString() ?? '';
  String get status => raw['status']?.toString() ?? 'PLANNED';
  String? get earliestBookingAt => raw['earliestBookingAt']?.toString();
  String get blockedReason => raw['blockedReason']?.toString() ?? '';
  String get result => raw['result']?.toString() ?? '';
  String get aftercare => raw['aftercare']?.toString() ?? '';
  bool allows(String action) => (raw['allowedActions'] as List? ?? []).any(
    (value) => value.toString().toUpperCase() == action.toUpperCase(),
  );
  List<TreatmentSessionItem> get items =>
      treatmentMaps(raw['items']).map(TreatmentSessionItem.fromJson).toList();
  List<int> get appointmentIds => {
    if (raw['appointmentId'] != null) treatmentInt(raw['appointmentId']),
    ...(raw['appointmentIds'] as List? ?? []).map(treatmentInt),
    ...items.map((item) => item.appointmentId).whereType<int>(),
  }.where((id) => id > 0).toList();
  List<TreatmentSessionItem> get outstandingItems => items
      .where(
        (item) =>
            item.status != 'PERFORMED' &&
            item.status != 'COMPLETED' &&
            item.status != 'SCHEDULED' &&
            item.status != 'IN_PROGRESS',
      )
      .toList();
}

class TreatmentEnrollment {
  final JsonMap raw;
  TreatmentEnrollment.fromJson(this.raw);
  int get id => treatmentInt(raw['id']);
  int get userId => treatmentInt(raw['userId']);
  int get version => treatmentInt(raw['version']);
  int get orderId => treatmentInt(raw['orderId']);
  String get name => raw['name']?.toString() ?? '';
  String get status => raw['status']?.toString() ?? '';
  String get customerName =>
      raw['customerName']?.toString() ?? 'Khách #$userId';
  String get expiryDate => raw['expiryDate']?.toString() ?? '';
  String get summary => raw['summary']?.toString() ?? '';
  int get completedSessions => treatmentInt(raw['completedSessions']);
  int get totalSessions => treatmentInt(raw['totalSessions']);
  double get progress =>
      ((double.tryParse('${raw['progressPercent']}') ?? 0) / 100)
          .clamp(0, 1)
          .toDouble();
  List<TreatmentSession> get sessions =>
      treatmentMaps(raw['sessions']).map(TreatmentSession.fromJson).toList();
  List<JsonMap> get changes => treatmentMaps(raw['changes']);
  List<JsonMap> get requests => treatmentMaps(raw['requests']);
  List<JsonMap> get audit => treatmentMaps(raw['audit']);
  bool allows(String action) => (raw['allowedActions'] as List? ?? []).any(
    (value) => value.toString().toUpperCase() == action.toUpperCase(),
  );
}

enum TreatmentArea { customer, staff, admin }

extension TreatmentAreaPath on TreatmentArea {
  String get path => switch (this) {
    TreatmentArea.customer => '/api/v1/spa/treatments',
    TreatmentArea.staff => '/api/v1/staff/spa/treatments',
    TreatmentArea.admin => '/api/v1/admin/spa/treatments',
  };
  String get label => switch (this) {
    TreatmentArea.customer => 'Lộ trình của tôi',
    TreatmentArea.staff => 'Công việc điều trị',
    TreatmentArea.admin => 'Quản trị lộ trình',
  };
}
