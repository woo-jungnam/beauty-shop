import { apiClient } from '../../../shared/api/client';
import React, { useEffect, useState } from 'react';
import { ShieldCheck, Truck, RotateCcw, Award, Mail, Phone, MapPin, Clock, X } from 'lucide-react';

const POLICIES = {
  terms: {
    title: 'Điều Khoản Sử Dụng',
    content: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13.5px', lineHeight: 1.6, color: '#334155' }}>
        <p><strong>Tài khoản:</strong> Bảo mật thông tin đăng nhập và kiểm tra thông tin người nhận trước khi gửi đơn hàng hoặc đặt lịch.</p>
        <p><strong>Đơn hàng:</strong> Xem lại sản phẩm, số lượng, giá và phí vận chuyển tại bước thanh toán. Theo dõi kết quả xử lý trong chi tiết đơn hàng.</p>
        <p><strong>Lịch hẹn:</strong> Kiểm tra dịch vụ, nhân viên, giờ trống và điều kiện được hiển thị trước khi xác nhận đặt lịch.</p>
        <p><strong>Hỗ trợ:</strong> Gửi yêu cầu đổi trả từ đơn hàng hoặc dùng thông tin liên hệ do cửa hàng công khai khi cần hỗ trợ.</p>
      </div>
    )
  },
  privacy: {
    title: 'Chính Sách Bảo Mật Thông Tin',
    content: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13.5px', lineHeight: 1.6, color: '#334155' }}>
        <p><strong>Thông tin bạn cung cấp:</strong> Hồ sơ, địa chỉ nhận hàng và thông tin đặt lịch được dùng trong các thao tác tương ứng trên hệ thống.</p>
        <p><strong>Quản lý hồ sơ:</strong> Bạn có thể xem và cập nhật các trường được hỗ trợ trong tài khoản của mình.</p>
        <p><strong>Bản tin:</strong> Đăng ký nhận email cần sự đồng ý của bạn; bạn có thể yêu cầu hủy đăng ký.</p>
        <p><strong>Trao đổi về dữ liệu:</strong> Dùng thông tin liên hệ do cửa hàng công khai nếu cần trao đổi về thông tin cá nhân đã cung cấp.</p>
      </div>
    )
  },
  shipping: {
    title: 'Thông Tin Giao Hàng',
    content: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13.5px', lineHeight: 1.6, color: '#334155' }}>
        <p><strong>Địa chỉ:</strong> Nhập đúng người nhận, số điện thoại và địa chỉ tại bước thanh toán.</p>
        <p><strong>Phí vận chuyển:</strong> Kiểm tra mức phí được hiển thị tại bước thanh toán trước khi gửi đơn hàng.</p>
        <p><strong>Theo dõi:</strong> Xem trạng thái xử lý và giao hàng trong chi tiết đơn. Thời điểm giao thực tế phụ thuộc quá trình xử lý đơn.</p>
        <p><strong>Khi cần hỗ trợ:</strong> Dùng mã đơn và thông tin liên hệ do cửa hàng công khai để trao đổi về việc giao hàng.</p>
      </div>
    )
  },
  returns: {
    title: 'Yêu Cầu Đổi Trả Theo Đơn Hàng',
    content: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13.5px', lineHeight: 1.6, color: '#334155' }}>
        <p><strong>Gửi yêu cầu:</strong> Mở đơn đã giao, chọn sản phẩm và số lượng muốn trả, điền lý do và đường dẫn bằng chứng nếu có. Hệ thống kiểm tra thời hạn và số lượng đủ điều kiện theo đơn.</p>
        <p><strong>Xét duyệt và nhận hàng:</strong> Yêu cầu được cửa hàng duyệt hoặc từ chối có lý do. Theo dõi kết quả và xác nhận nhận hàng trong chi tiết yêu cầu.</p>
        <p><strong>Hoàn tiền:</strong> Khoản hoàn được xử lý theo yêu cầu đã duyệt, hàng thực nhận và số tiền thực thu. Xem trạng thái xác nhận hoàn trong đơn hàng; phí phát sinh và cách nhận lại hàng cần được trao đổi với cửa hàng.</p>
      </div>
    )
  },
  authentic: {
    title: 'Thông Tin Sản Phẩm',
    content: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13.5px', lineHeight: 1.6, color: '#334155' }}>
        <p><strong>Trước khi mua:</strong> Xem thương hiệu, mô tả và thông tin sản phẩm được cửa hàng công bố trên trang chi tiết.</p>
        <p><strong>Chọn đúng phân loại:</strong> Kiểm tra phân loại, số lượng, giá và khả năng cung ứng trước khi gửi đơn.</p>
        <p><strong>Khi có thắc mắc:</strong> Trao đổi với cửa hàng về nguồn gốc, nhãn hàng hóa hoặc thông tin chưa rõ trước khi chọn mua.</p>
      </div>
    )
  }
};

export const CustomerFooter = ({ onNavigate }) => {
  const [activePolicy, setActivePolicy] = useState(null);
  const [storeContact, setStoreContact] = useState({ address: '', hotline: '', email: '' });
  useEffect(() => {
    let active = true;
    apiClient.get('/api/v1/system-configs/public').then(response => {
      if (!active || response?.success === false) return;
      const configs = response?.data ?? response;
      const text = key => typeof configs?.[key] === 'string' ? configs[key].trim() : '';
      setStoreContact({ address: text('store.address'), hotline: text('store.hotline'), email: text('store.email') });
    }).catch(() => {});
    return () => { active = false; };
  }, []);
  const hotlineNumber = storeContact.hotline.replace(/[\s().-]/g, '');
  const hotlineHref = /^\+?\d{6,15}$/.test(hotlineNumber) ? `tel:${hotlineNumber}` : null;
  const emailHref = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(storeContact.email)
    ? `mailto:${encodeURIComponent(storeContact.email).replace('%40', '@')}` : null;
  const contactLinkStyle = { color: 'inherit', textDecoration: 'underline' };
  const [newsletterConsent, setNewsletterConsent] = useState(false);
  const [newsletterEmail, setNewsletterEmail] = useState('');
  const [newsletterFeedback, setNewsletterFeedback] = useState('');
  const [newsletterBusy, setNewsletterBusy] = useState(false);
  const subscribe = async () => { if (newsletterBusy) return; if (!newsletterConsent) { setNewsletterFeedback('Vui lòng đồng ý nhận bản tin trước khi đăng ký.'); return; } if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(newsletterEmail)) { setNewsletterFeedback('Vui lòng nhập email hợp lệ.'); return; } setNewsletterBusy(true); try { const response = await apiClient.post('/api/v1/newsletter/subscriptions', { email: newsletterEmail, consent: newsletterConsent }); setNewsletterFeedback(response?.message || 'Đã đăng ký nhận bản tin.'); setNewsletterEmail(''); } catch (error) { setNewsletterFeedback(error.message); } finally { setNewsletterBusy(false); } };
  return (
    <footer style={{
      backgroundColor: '#8C2A47',
      color: '#FFFFFF',
      marginTop: 'auto',
      borderTop: '1px solid rgba(255, 255, 255, 0.08)'
    }}>
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
              title="Xem thông tin sản phẩm"
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
                  Thông Tin Sản Phẩm
                </h4>
                <p style={{ color: '#FCE7EC', fontSize: '12px', margin: 0, lineHeight: 1.5 }}>
                  Xem thương hiệu, mô tả và phân loại trước khi chọn mua.
                </p>
              </div>
            </div>

            <div
              style={{ display: 'flex', alignItems: 'flex-start', gap: '14px', cursor: 'pointer' }}
              onClick={() => setActivePolicy('shipping')}
              title="Xem thông tin giao hàng"
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
                  Phí Giao Hàng Tại Thanh Toán
                </h4>
                <p style={{ color: '#FCE7EC', fontSize: '12px', margin: 0, lineHeight: 1.5 }}>
                  Nhập địa chỉ và kiểm tra phí vận chuyển khi thanh toán.
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
                  Yêu Cầu Đổi Trả Theo Đơn
                </h4>
                <p style={{ color: '#FCE7EC', fontSize: '12px', margin: 0, lineHeight: 1.5 }}>
                  Gửi mặt hàng, số lượng và lý do để cửa hàng xét duyệt.
                </p>
              </div>
            </div>

            <div
              style={{ display: 'flex', alignItems: 'flex-start', gap: '14px', cursor: 'pointer' }}
              onClick={() => onNavigate('spa')}
              title="Khám phá dịch vụ Spa"
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
                  Dịch Vụ Spa
                </h4>
                <p style={{ color: '#FCE7EC', fontSize: '12px', margin: 0, lineHeight: 1.5 }}>
                  Xem dịch vụ, nhân viên và khung giờ còn trống trước khi đặt lịch.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div style={{ padding: '48px 0 32px 0' }}>
        <div className="customer-container">
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: '36px'
          }}>
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
                Mua sắm mỹ phẩm và đặt lịch dịch vụ làm đẹp. Theo dõi đơn hàng, lịch hẹn và các yêu cầu của bạn trong tài khoản.
              </p>
              <div style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '13px', color: '#FCE7EC' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <MapPin size={15} color="#FFCCD5" />
                  <span>Địa chỉ cửa hàng: {storeContact.address
                    ? <a href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(storeContact.address)}`} target="_blank" rel="noopener noreferrer" style={contactLinkStyle}>{storeContact.address}</a>
                    : 'Chưa cập nhật.'}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Phone size={15} color="#FFCCD5" />
                  <span>Hotline CSKH: {hotlineHref
                    ? <a href={hotlineHref} style={contactLinkStyle}>{storeContact.hotline}</a>
                    : 'Chưa cập nhật.'}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Mail size={15} color="#FFCCD5" />
                  <span>Email liên hệ: {emailHref
                    ? <a href={emailHref} style={contactLinkStyle}>{storeContact.email}</a>
                    : 'Chưa cập nhật.'}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Clock size={15} color="#FFCCD5" />
                  <button type="button" onClick={() => onNavigate('booking')} style={{ background: 'none', border: 0, padding: 0, color: 'inherit', font: 'inherit', cursor: 'pointer', textAlign: 'left', textDecoration: 'underline' }}>Xem giờ trống và đặt lịch Spa</button>
                </div>
              </div>
            </div>

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

            <div>
              <h4 style={{ color: '#FFFFFF', fontSize: '14.5px', fontWeight: 800, marginBottom: '16px' }}>
                Dịch Vụ Làm Đẹp
              </h4>
              <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '13px', color: '#CBD5E1' }}>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('spa')}>Xem Danh Sách Dịch Vụ</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('booking')}>Chọn Nhân Viên Và Giờ Trống</li>
                <li style={{ cursor: 'pointer' }} onClick={() => onNavigate('booking')}>Đăng Ký Đặt Lịch Hẹn Trực Tuyến</li>
              </ul>
            </div>

            <div>
              <h4 style={{ color: '#FFFFFF', fontSize: '14.5px', fontWeight: 800, marginBottom: '16px' }}>
                Khuyến Mãi & Bản Tin
              </h4>
              <p style={{ fontSize: '12px', color: '#CBD5E1', marginBottom: '12px' }}>
                Đăng ký email để nhận thông tin ưu đãi và sản phẩm mới:
              </p>
              <div style={{ display: 'flex', gap: '6px' }}>
                <input
                  type="email"
                  placeholder="Nhập email của bạn..." value={newsletterEmail} onChange={event => setNewsletterEmail(event.target.value)} aria-label="Email đăng ký bản tin"
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
                  onClick={subscribe} disabled={newsletterBusy}
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

              <label style={{ display: 'flex', gap: 6, fontSize: 12, marginTop: 10 }}><input type="checkbox" checked={newsletterConsent} onChange={event => setNewsletterConsent(event.target.checked)} />Tôi đồng ý nhận email ưu đãi và có thể hủy đăng ký.</label>
              {newsletterFeedback && <p role="status">{newsletterFeedback}</p>}
            </div>
          </div>

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
              © 2026 BeautyShop. Mỹ phẩm & dịch vụ làm đẹp.
            </div>
            <div style={{ display: 'flex', gap: '16px' }}>
              <span style={{ cursor: 'pointer', textDecoration: 'underline' }} onClick={() => setActivePolicy('terms')}>Điều khoản sử dụng</span>
              <span>•</span>
              <span style={{ cursor: 'pointer', textDecoration: 'underline' }} onClick={() => setActivePolicy('privacy')}>Chính sách bảo mật</span>
              <span>•</span>
              <span style={{ cursor: 'pointer', textDecoration: 'underline' }} onClick={() => setActivePolicy('shipping')}>Thông tin giao hàng</span>
            </div>
          </div>
        </div>
      </div>

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
