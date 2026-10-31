import assert from 'node:assert/strict';
import {
  getOrderType,
  getOrderTypeBadge,
  getOrderStatusBadge,
  getPaymentStatusBadge,
  getPaymentMethodLabel,
  getReturnStatusBadge,
  getOutreachStatusBadge,
} from '../formatters.js';

// Test 1: getOrderType and getOrderTypeBadge
const productOrder = { id: 1, totalAmount: 500000 };
assert.equal(getOrderType(productOrder), 'PRODUCT');
assert.equal(getOrderTypeBadge(productOrder).text, 'Sản phẩm mỹ phẩm');

const packageOrder = { id: 2, servicePackageId: 10, totalAmount: 2000000 };
assert.equal(getOrderType(packageOrder), 'SPA_PACKAGE');
assert.equal(getOrderTypeBadge(packageOrder).text, 'Gói liệu trình Spa');

const visitOrder = { id: 3, appointmentId: 45, spaVisitItems: [{ serviceName: 'Chăm sóc da', price: 300000 }] };
assert.equal(getOrderType(visitOrder), 'SPA_VISIT');
assert.equal(getOrderTypeBadge(visitOrder).text, 'Hóa đơn tại quầy');

// Test 2: getOrderStatusBadge with new and complete Vietnamese translations
assert.equal(getOrderStatusBadge('COMPLETED').text, 'Hoàn tất');
assert.equal(getOrderStatusBadge('REQUESTED').text, 'Yêu cầu đổi trả');
assert.equal(getOrderStatusBadge('APPROVED').text, 'Đã duyệt đổi trả');
assert.equal(getOrderStatusBadge('RECEIVED').text, 'Đã nhận hàng trả');
assert.equal(getOrderStatusBadge('REJECTED').text, 'Từ chối đổi trả');
assert.equal(getOrderStatusBadge('REFUNDED').text, 'Đã hoàn tiền');
assert.equal(getOrderStatusBadge('DELIVERY_FAILED').text, 'Giao không thành công');
assert.equal(getOrderStatusBadge('RETURN_TO_SENDER').text, 'Hàng hoàn về kho');

// Test 3: getPaymentStatusBadge
assert.equal(getPaymentStatusBadge('PARTIALLY_PAID').text, 'Thanh toán một phần');
assert.equal(getPaymentStatusBadge('UNPAID').text, 'Chưa thanh toán');
assert.equal(getPaymentStatusBadge('OVERDUE').text, 'Quá hạn thanh toán');
assert.equal(getPaymentStatusBadge('PAID').text, 'Đã thanh toán');

// Test 4: getPaymentMethodLabel
assert.equal(getPaymentMethodLabel('WAIVE_WALK_IN'), 'Miễn cọc tại quầy');
assert.equal(getPaymentMethodLabel('DEPOSIT_AT_COUNTER'), 'Đặt cọc tại quầy');
assert.equal(getPaymentMethodLabel('PAY_FULL'), 'Thanh toán đủ 100% (VietQR)');
assert.equal(getPaymentMethodLabel('TICKET'), 'Khấu trừ vé liệu trình');

// Test 5: Return and Outreach status badges
assert.equal(getReturnStatusBadge('REQUESTED').text, 'Chờ xét duyệt');
assert.equal(getReturnStatusBadge('APPROVED').text, 'Đã chấp thuận');
assert.equal(getReturnStatusBadge('RECEIVED').text, 'Đã nhận hàng trả');
assert.equal(getReturnStatusBadge('REFUNDED').text, 'Đã hoàn tiền');

assert.equal(getOutreachStatusBadge('OPEN').text, 'Chưa liên hệ');
assert.equal(getOutreachStatusBadge('CONTACTED').text, 'Đã liên hệ');
assert.equal(getOutreachStatusBadge('RESOLVED').text, 'Đã xử lý xong');

console.log('✓ All order and enum helper tests passed successfully.');
