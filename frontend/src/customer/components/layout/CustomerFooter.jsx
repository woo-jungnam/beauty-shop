import React from 'react';
import { ShieldCheck, Truck, RotateCcw, Award, Mail, Phone, MapPin, Clock } from 'lucide-react';

export const CustomerFooter = ({ onNavigate }) => {
  return (
    <footer style={{
      backgroundColor: '#8C2A47',
      color: '#FFFFFF',
      marginTop: 'auto',
      borderTop: '1px solid rgba(255, 255, 255, 0.08)'
    }}>
      {/* 1. Value Proposition Pillars - Hasaki 4 Cam Kết Vàng */}
      <div style={{
        borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
        padding: '32px 0',
        backgroundColor: '#B84365'
      }}>
        <div className="customer-container">
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            gap: '24px'
          }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '14px' }}>
              <div style={{
                width: '44px',
                height: '44px',
                borderRadius: '50%',
                background: 'rgba(255, 255, 255, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}>
                <ShieldCheck size={22} color="#FFCCD5" />
              </div>
              <div>
                <h4 style={{ color: '#FFFFFF', fontSize: '14px', fontWeight: 800, margin: '0 0 4px 0' }}>
                  100% Chính Hãng - Giá Trị Thật
                </h4>
                <p style={{ color: '#FCE7EC', fontSize: '12px', margin: 0, lineHeight: 1.5 }}>
                  Cam kết xuất xứ rõ ràng, tem phụ chuẩn Bộ Y Tế. Đền bù 200% nếu phát hiện hàng giả.
                </p>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '14px' }}>
              <div style={{
                width: '44px',
                height: '44px',
                borderRadius: '50%',
                background: 'rgba(255, 255, 255, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}>
                <Truck size={22} color="#FFCCD5" />
              </div>
              <div>
                <h4 style={{ color: '#FFFFFF', fontSize: '14px', fontWeight: 800, margin: '0 0 4px 0' }}>
                  Giao Nhanh 2H NowFree
                </h4>
                <p style={{ color: '#FCE7EC', fontSize: '12px', margin: 0, lineHeight: 1.5 }}>
                  Giao nhanh trong 2 giờ nội thành TP.HCM & Hà Nội. Miễn phí vận chuyển toàn quốc từ 249.000 ₫.
                </p>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '14px' }}>
              <div style={{
                width: '44px',
                height: '44px',
                borderRadius: '50%',
                background: 'rgba(255, 255, 255, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}>
                <RotateCcw size={22} color="#FFCCD5" />
              </div>
              <div>
                <h4 style={{ color: '#FFFFFF', fontSize: '14px', fontWeight: 800, margin: '0 0 4px 0' }}>
                  Đổi Trả Miễn Phí 14 Ngày
                </h4>
                <p style={{ color: '#FCE7EC', fontSize: '12px', margin: 0, lineHeight: 1.5 }}>
                  Hỗ trợ đổi trả hoặc hoàn tiền 100% khi sản phẩm còn nguyên tem mác hoặc phát sinh lỗi kích ứng.
                </p>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '14px' }}>
              <div style={{
                width: '44px',
                height: '44px',
                borderRadius: '50%',
                background: 'rgba(255, 255, 255, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}>
                <Award size={22} color="#FFCCD5" />
              </div>
              <div>
                <h4 style={{ color: '#FFFFFF', fontSize: '14px', fontWeight: 800, margin: '0 0 4px 0' }}>
                  Beauty Clinic & Spa
                </h4>
                <p style={{ color: '#FCE7EC', fontSize: '12px', margin: 0, lineHeight: 1.5 }}>
                  Bác sĩ chuyên khoa Da Liễu khám và soi da miễn phí. Dàn máy móc thẩm mỹ hiện đại chuẩn FDA.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 2. Main Footer Navigation Columns */}
      <div style={{ padding: '48px 0 32px 0' }}>
        <div className="customer-container">
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: '36px'
          }}>
            {/* Column 1: Brand Introduction */}
            <div style={{ gridColumn: 'span 1' }}>
              <span style={{
                fontFamily: 'var(--font-sans)',
                fontSize: '24px',
                fontWeight: 900,
                color: '#FFFFFF',
                letterSpacing: '0.04em'
              }}>
                BEAUTY<span style={{ color: '#FFCCD5' }}>SHOP</span>
              </span>
              <p style={{
                fontSize: '13px',
                color: '#F8D0DA',
                marginTop: '12px',
                lineHeight: 1.6
              }}>
                Hệ thống chuỗi cửa hàng mỹ phẩm chính hãng và phòng khám da liễu Clinic & Spa. Cam kết chất lượng thật, giá trị thật cho người tiêu dùng Việt Nam.
              </p>
              <div style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '13px', color: '#FCE7EC' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <MapPin size={15} color="#FFCCD5" />
                  <span>Trụ sở: 123 Lê Lợi, Bến Nghé, Quận 1, TP. Hồ Chí Minh</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Phone size={15} color="#FFCCD5" />
                  <span>Hotline CSKH: 1800 6324 (Miễn phí 08:00 - 22:00)</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Clock size={15} color="#FFCCD5" />
                  <span>Giờ mở cửa Clinic: 09:00 - 21:00 (Tất cả các ngày)</span>
                </div>
              </div>
            </div>

            {/* Column 2: Cosmetics Catalog */}
            <div>
              <h4 style={{ color: '#FFFFFF', fontSize: '14.5px', fontWeight: 800, marginBottom: '16px' }}>
                Danh Mục Nổi Bật
              </h4>
              <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '13px', color: '#CBD5E1' }}>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('products')}>Chăm sóc da mặt chuyên sâu</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('products')}>Kem chống nắng quang phổ rộng</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('products')}>Sữa rửa mặt & Tẩy trang dịu nhẹ</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('products')}>Serum B5 & Tinh chất phục hồi</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('products')}>Dưỡng ẩm & Tái tạo hàng rào da</li>
              </ul>
            </div>

            {/* Column 3: Spa Treatments */}
            <div>
              <h4 style={{ color: '#FFFFFF', fontSize: '14.5px', fontWeight: 800, marginBottom: '16px' }}>
                Dịch Vụ Clinic & Spa
              </h4>
              <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '13px', color: '#CBD5E1' }}>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('spa')}>Khám & Soi Da Miễn Phí Với Bác Sĩ</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('spa')}>Aqua Peel Làm Sạch Sâu Lỗ Chân Lông</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('spa')}>Trị Mụn Y Khoa Bio-Light Không Thâm</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('spa')}>Điện Di Tinh Chất Phục Hồi Chuyên Sâu</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('booking')}>Đăng Ký Đặt Lịch Hẹn Trực Tuyến</li>
              </ul>
            </div>

            {/* Column 4: Newsletter & Certification */}
            <div>
              <h4 style={{ color: '#FFFFFF', fontSize: '14.5px', fontWeight: 800, marginBottom: '16px' }}>
                Khuyến Mãi & Bản Tin
              </h4>
              <p style={{ fontSize: '12px', color: '#CBD5E1', marginBottom: '12px' }}>
                Đăng ký email để nhận voucher giảm 50.000 ₫ và cập nhật deal giờ vàng:
              </p>
              <div style={{ display: 'flex', gap: '6px' }}>
                <input
                  type="email"
                  placeholder="Nhập email của bạn..."
                  style={{
                    padding: '8px 12px',
                    borderRadius: '4px',
                    border: '1px solid rgba(255, 255, 255, 0.25)',
                    backgroundColor: 'rgba(255, 255, 255, 0.12)',
                    color: '#FFFFFF',
                    fontSize: '12px',
                    flex: 1,
                    outline: 'none'
                  }}
                />
                <button
                  onClick={() => alert('Cảm ơn bạn đã đăng ký nhận bản tin BeautyShop!')}
                  style={{
                    padding: '8px 16px',
                    borderRadius: '4px',
                    backgroundColor: 'var(--c-deal-red, #E31837)',
                    color: '#FFFFFF',
                    border: 'none',
                    fontWeight: 700,
                    fontSize: '12px',
                    cursor: 'pointer'
                  }}
                >
                  Đăng ký
                </button>
              </div>

              <div style={{ marginTop: '18px' }}>
                <span style={{
                  display: 'inline-block',
                  padding: '5px 10px',
                  background: 'rgba(255, 255, 255, 0.1)',
                  border: '1px solid rgba(255, 255, 255, 0.25)',
                  borderRadius: '4px',
                  fontSize: '10.5px',
                  color: '#FFCCD5',
                  fontWeight: 700
                }}>
                  ✓ ĐÃ THÔNG BÁO BỘ CÔNG THƯƠNG
                </span>
              </div>
            </div>
          </div>

          {/* Copyright bar */}
          <div style={{
            borderTop: '1px solid rgba(255, 255, 255, 0.1)',
            marginTop: '36px',
            paddingTop: '18px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '12px',
            fontSize: '12px',
            color: '#94A3B8'
          }}>
            <div>
              © 2026 BeautyShop. Hệ thống Mỹ phẩm & Clinic phân phối chính hãng.
            </div>
            <div style={{ display: 'flex', gap: '16px' }}>
              <span style={{ cursor: 'pointer' }}>Điều khoản sử dụng</span>
              <span>•</span>
              <span style={{ cursor: 'pointer' }}>Chính sách bảo mật</span>
              <span>•</span>
              <span style={{ cursor: 'pointer' }}>Chính sách giao hàng 2H</span>
            </div>
          </div>
        </div>
      </div>
    </footer>
  );
};
