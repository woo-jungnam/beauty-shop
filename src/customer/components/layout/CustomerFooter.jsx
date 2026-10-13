import React, { useState } from 'react';
import { ShieldCheck, Truck, RotateCcw, Award, Mail, Phone, MapPin, Clock, X } from 'lucide-react';

const POLICIES = {
  terms: {
    title: 'Điều Khoản Sử Dụng',
    content: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13.5px', lineHeight: 1.6, color: '#334155' }}>
        <p><strong>1. Giới thiệu:</strong> Chào mừng bạn đến với hệ thống BeautyShop. Khi sử dụng dịch vụ trên website, quý khách đồng ý tuân thủ toàn bộ các điều khoản và quy định được ban hành tại đây.</p>
        <p><strong>2. Tài khoản & Bảo mật:</strong> Khách hàng có trách nhiệm bảo mật thông tin đăng nhập cá nhân. Mọi giao dịch phát sinh qua tài khoản đã xác thực được xem là hợp lệ.</p>
        <p><strong>3. Đặt hàng & Xác nhận:</strong> Khi quý khách đặt hàng, hệ thống sẽ tự động gửi email/thông báo xác nhận. BeautyShop có quyền từ chối hoặc hủy đơn trong các trường hợp nghi ngờ gian lận hoặc sai sót giá niêm yết do sự cố hệ thống.</p>
        <p><strong>4. Quyền sở hữu trí tuệ:</strong> Toàn bộ nội dung, hình ảnh, nhãn hiệu thuộc bản quyền của BeautyShop hoặc các thương hiệu đối tác được ủy quyền.</p>
      </div>
    )
  },
  privacy: {
    title: 'Chính Sách Bảo Mật Thông Tin',
    content: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13.5px', lineHeight: 1.6, color: '#334155' }}>
        <p><strong>1. Thu thập thông tin:</strong> Chúng tôi chỉ thu thập các thông tin cần thiết phục vụ đơn hàng và dịch vụ Clinic (Họ tên, Số điện thoại, Email, Địa chỉ nhận hàng).</p>
        <p><strong>2. Mục đích sử dụng:</strong> Thông tin được sử dụng để đóng gói vận chuyển, cấp mã vé điện tử Spa, thông báo tình trạng đơn hàng và tư vấn chăm sóc da chuyên sâu.</p>
        <p><strong>3. Cam kết bảo mật:</strong> BeautyShop cam kết tuyệt đối không chia sẻ, mua bán hay tiết lộ dữ liệu người dùng cho bên thứ ba ngoại trừ các đơn vị vận chuyển đối tác.</p>
        <p><strong>4. Quyền của khách hàng:</strong> Quý khách có quyền kiểm tra, cập nhật hoặc yêu cầu hủy bỏ thông tin cá nhân bất kỳ lúc nào tại mục Quản lý tài khoản.</p>
      </div>
    )
  },
  shipping: {
    title: 'Chính Sách Giao Hàng 2H NowFree',
    content: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13.5px', lineHeight: 1.6, color: '#334155' }}>
        <p><strong>1. Giao nhanh 2H:</strong> Áp dụng cho đơn hàng nội thành TP.HCM & Hà Nội đặt trong khung giờ từ 08:00 đến 20:00 hàng ngày.</p>
        <p><strong>2. Biểu phí vận chuyển:</strong> Miễn phí vận chuyển toàn quốc cho tất cả đơn hàng từ <strong>249.000 ₫</strong>. Đơn hàng dưới 249.000 ₫ áp dụng phí đồng giá 25.000 ₫ - 35.000 ₫.</p>
        <p><strong>3. Thời gian giao hàng:</strong><br />• Nội thành: Giao nhanh 2H hoặc trong ngày theo yêu cầu.<br />• Ngoại thành & Các tỉnh: Từ 1 - 3 ngày làm việc qua đối tác vận chuyển hỏa tốc.</p>
        <p><strong>4. Đồng kiểm khi nhận:</strong> Quý khách được quyền kiểm tra ngoại quan gói hàng (nguyên seal, tem phụ) trước khi ký nhận và thanh toán.</p>
      </div>
    )
  },
  returns: {
    title: 'Chính Sách Đổi Trả Miễn Phí 14 Ngày',
    content: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13.5px', lineHeight: 1.6, color: '#334155' }}>
        <p><strong>1. Điều kiện đổi trả:</strong> Hỗ trợ đổi trả trong vòng 14 ngày kể từ khi nhận hàng. Sản phẩm còn nguyên tem mác, chưa qua sử dụng hoặc phát sinh dị ứng kích ứng da có xác nhận từ chuyên viên da liễu.</p>
        <p><strong>2. Chi phí đổi trả:</strong> BeautyShop hỗ trợ 100% chi phí vận chuyển đổi trả nếu lỗi xuất phát từ nhà sản xuất hoặc do đóng gói sai quy cách.</p>
        <p><strong>3. Thời gian hoàn tiền:</strong> Hoàn tiền vào tài khoản ngân hàng hoặc ví điểm thưởng trong vòng 1 - 3 ngày làm việc sau khi tiếp nhận sản phẩm hoàn về.</p>
      </div>
    )
  },
  authentic: {
    title: 'Cam Kết 100% Chính Hãng - Giá Trị Thật',
    content: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13.5px', lineHeight: 1.6, color: '#334155' }}>
        <p><strong>1. Nguồn gốc minh bạch:</strong> 100% sản phẩm phân phối tại BeautyShop đều có tem phụ tiếng Việt chuẩn Bộ Y Tế, hóa đơn chứng từ VAT và nhập khẩu chính ngạch từ các thương hiệu dược mỹ phẩm hàng đầu.</p>
        <p><strong>2. Bồi thường 200%:</strong> Cam kết hoàn tiền và đền bù 200% giá trị sản phẩm nếu phát hiện bất kỳ hàng giả, hàng nhái nào trong hệ thống.</p>
        <p><strong>3. Quản lý date FEFO:</strong> 100% sản phẩm có hạn sử dụng xa, được quản lý chặt chẽ theo quy trình xuất nhập FEFO trong kho bảo quản đạt chuẩn GSP.</p>
      </div>
    )
  }
};

export const CustomerFooter = ({ onNavigate }) => {
  const [activePolicy, setActivePolicy] = useState(null);
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
            <div
              style={{ display: 'flex', alignItems: 'flex-start', gap: '14px', cursor: 'pointer' }}
              onClick={() => setActivePolicy('authentic')}
              title="Xem cam kết chính hãng"
            >
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

            <div
              style={{ display: 'flex', alignItems: 'flex-start', gap: '14px', cursor: 'pointer' }}
              onClick={() => setActivePolicy('shipping')}
              title="Xem chính sách giao nhanh 2H"
            >
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

            <div
              style={{ display: 'flex', alignItems: 'flex-start', gap: '14px', cursor: 'pointer' }}
              onClick={() => setActivePolicy('returns')}
              title="Xem chính sách đổi trả"
            >
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

            <div
              style={{ display: 'flex', alignItems: 'flex-start', gap: '14px', cursor: 'pointer' }}
              onClick={() => onNavigate('spa')}
              title="Khám phá dịch vụ Spa & Clinic"
            >
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
              <div
                onClick={() => onNavigate('')}
                style={{ cursor: 'pointer', display: 'inline-block' }}
                title="Về trang chủ BeautyShop"
              >
                <span style={{
                  fontFamily: 'var(--font-sans)',
                  fontSize: '24px',
                  fontWeight: 900,
                  color: '#FFFFFF',
                  letterSpacing: '0.04em'
                }}>
                  BEAUTY<span style={{ color: '#FFCCD5' }}>SHOP</span>
                </span>
              </div>
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
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('products?categoryId=100&category=cham-soc-da-mat&name=' + encodeURIComponent('Chăm sóc da mặt'))}>Chăm sóc da mặt chuyên sâu</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('products?categoryId=6&category=kem-chong-nang&name=' + encodeURIComponent('Kem chống nắng'))}>Kem chống nắng quang phổ rộng</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('products?categoryId=1&category=sua-rua-mat&name=' + encodeURIComponent('Sữa rửa mặt'))}>Sữa rửa mặt & Tẩy trang dịu nhẹ</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('products?categoryId=4&category=serum&name=' + encodeURIComponent('Serum'))}>Serum B5 & Tinh chất phục hồi</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('products?categoryId=5&category=kem-duong&name=' + encodeURIComponent('Kem dưỡng'))}>Dưỡng ẩm & Tái tạo hàng rào da</li>
              </ul>
            </div>

            {/* Column 3: Spa Treatments */}
            <div>
              <h4 style={{ color: '#FFFFFF', fontSize: '14.5px', fontWeight: 800, marginBottom: '16px' }}>
                Dịch Vụ Clinic & Spa
              </h4>
              <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '13px', color: '#CBD5E1' }}>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('spa')}>Khám & Soi Da Miễn Phí Với Bác Sĩ</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('booking?serviceId=1')}>Aqua Peel Làm Sạch Sâu Lỗ Chân Lông</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('booking?serviceId=2')}>Trị Mụn Y Khoa Bio-Light Không Thâm</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('booking?serviceId=3')}>Điện Di Tinh Chất Phục Hồi Chuyên Sâu</li>
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
              <span style={{ cursor: 'pointer', textDecoration: 'underline' }} onClick={() => setActivePolicy('terms')}>Điều khoản sử dụng</span>
              <span>•</span>
              <span style={{ cursor: 'pointer', textDecoration: 'underline' }} onClick={() => setActivePolicy('privacy')}>Chính sách bảo mật</span>
              <span>•</span>
              <span style={{ cursor: 'pointer', textDecoration: 'underline' }} onClick={() => setActivePolicy('shipping')}>Chính sách giao hàng 2H</span>
            </div>
          </div>
        </div>
      </div>

      {/* 3. Policy & Commitment Modal */}
      {activePolicy && POLICIES[activePolicy] && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.65)',
            backdropFilter: 'blur(4px)',
            zIndex: 99999,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px'
          }}
          onClick={() => setActivePolicy(null)}
        >
          <div
            style={{
              backgroundColor: '#FFFFFF',
              color: '#0F172A',
              borderRadius: '12px',
              maxWidth: '560px',
              width: '100%',
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
              overflow: 'hidden'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '16px 20px',
              backgroundColor: '#8C2A47',
              color: '#FFFFFF'
            }}>
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 800 }}>
                {POLICIES[activePolicy].title}
              </h3>
              <button
                onClick={() => setActivePolicy(null)}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: '#FFFFFF',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '4px',
                  borderRadius: '4px'
                }}
              >
                <X size={20} />
              </button>
            </div>
            <div style={{ padding: '24px', maxHeight: '70vh', overflowY: 'auto' }}>
              {POLICIES[activePolicy].content}
            </div>
            <div style={{
              padding: '12px 20px',
              borderTop: '1px solid #E2E8F0',
              display: 'flex',
              justifyContent: 'flex-end',
              backgroundColor: '#F8FAFC'
            }}>
              <button
                onClick={() => setActivePolicy(null)}
                style={{
                  padding: '8px 18px',
                  borderRadius: '6px',
                  backgroundColor: '#8C2A47',
                  color: '#FFFFFF',
                  border: 'none',
                  fontWeight: 600,
                  fontSize: '13px',
                  cursor: 'pointer'
                }}
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </footer>
  );
};
