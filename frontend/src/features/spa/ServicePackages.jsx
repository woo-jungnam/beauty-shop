import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  Calendar,
  Check,
  CheckCircle2,
  Clock,
  HelpCircle,
  Info,
  Phone,
  RefreshCw,
  Search,
  ShieldCheck,
  Sparkles,
  Ticket
} from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { useAuth } from '../../app/providers/AuthProvider';
import { Button } from '../../shared/ui/Button';
import { Modal } from '../../shared/ui/Modal';
import { money, unwrap, errorMessage, idempotencyKey } from '../treatments/treatmentApi';
import { openTreatmentPayment } from '../treatments/paymentNavigation';
import { resolveMediaUrl } from '../../shared/utils/formatters';
import './spa-benefits.css';

export const ServicePackages = ({ onNavigate }) => {
  const { isAuthenticated, user } = useAuth();
  const purchaseKeys = useRef(new Map());
  useEffect(() => {
    purchaseKeys.current.clear();
  }, [user?.id]);

  const [packages, setPackages] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [revision, setRevision] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');

  const selectPackage = (pack) => {
    if (!purchaseKeys.current.has(pack.id)) {
      purchaseKeys.current.set(pack.id, idempotencyKey());
    }
    setSelected({ ...pack, purchaseKey: purchaseKeys.current.get(pack.id) });
  };

  useEffect(() => {
    let active = true;
    apiClient
      .get('/api/v1/spa/services/packages')
      .then((r) => {
        const data = unwrap(r);
        if (!Array.isArray(data)) throw new Error('Dữ liệu gói không hợp lệ');
        if (active) setPackages(data);
      })
      .catch((e) => {
        if (active) setError(errorMessage(e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [revision]);

  const filteredPackages = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return packages;
    return packages.filter(
      (p) =>
        (p.name || '').toLowerCase().includes(q) ||
        (p.description || '').toLowerCase().includes(q) ||
        (p.items || []).some((item) =>
          (item.serviceName || '').toLowerCase().includes(q)
        )
    );
  }, [packages, searchQuery]);

  return (
    <section className="spa-benefits" aria-labelledby="spa-packages-heading">
      {/* Breadcrumb Navigation */}
      <nav className="spa-benefit-breadcrumb" aria-label="Đường dẫn gói dịch vụ">
        <button type="button" onClick={() => onNavigate?.('')}>
          <ArrowLeft size={14} /> Trang chủ
        </button>
        <span aria-hidden="true">/</span>
        <span style={{ color: 'var(--c-primary, #AD3F61)', fontWeight: 600 }}>
          Gói dịch vụ
        </span>
      </nav>

      {/* Hero Banner with Editorial Luxury Aesthetic */}
      <div className="spa-benefit-hero">
        <span className="spa-benefit-eyebrow">
          <Sparkles size={13} /> Đặc quyền chăm sóc da chuyên sâu
        </span>
        <h1 id="spa-packages-heading">Gói dịch vụ nhiều lượt</h1>
        <p className="spa-benefit-hero-desc">
          Mua trước quyền sử dụng từng dịch vụ để nhận mức giá ưu đãi tốt nhất.
          Chủ động đặt lịch từng buổi qua Web & Mobile, quyền lợi được bảo lưu
          và đối soát minh bạch tại quầy. Lộ trình chăm sóc da có kế hoạch riêng
          tại mục Liệu trình.
        </p>

        {/* Trust Highlights */}
        <div className="spa-benefit-trust-bar">
          <div className="spa-benefit-trust-chip">
            <Sparkles size={15} /> Tiết kiệm chi phí so với mua lẻ
          </div>
          <div className="spa-benefit-trust-chip">
            <Calendar size={15} /> Linh hoạt tự chọn thời gian từng buổi
          </div>
          <div className="spa-benefit-trust-chip">
            <ShieldCheck size={15} /> Bảo toàn quyền lợi & quyết toán minh bạch
          </div>
          <div className="spa-benefit-trust-chip">
            <Ticket size={15} /> Ví vé điện tử tự động cấn trừ sau buổi
          </div>
        </div>

        {/* Action Shortcuts */}
        <div className="spa-benefit-actions">
          <Button variant="outline" onClick={() => onNavigate?.('booking')}>
            Đặt dịch vụ lẻ / dùng vé
          </Button>
          <Button
            variant="outline"
            onClick={() => onNavigate?.('profile?tab=tickets')}
          >
            Vé của tôi
          </Button>
          <Button variant="outline" onClick={() => onNavigate?.('treatments')}>
            Lộ trình chăm sóc da
          </Button>
        </div>
      </div>

      {/* Search & Counter Filter Bar */}
      {!loading && !error && packages.length > 0 && (
        <div className="spa-benefit-filter-bar">
          <div className="spa-benefit-search-box">
            <Search size={16} />
            <input
              type="text"
              placeholder="Tìm kiếm gói dịch vụ hoặc tên dịch vụ thành phần…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Tìm kiếm gói dịch vụ"
            />
          </div>
          <span className="spa-benefit-count-badge">
            Hiển thị {filteredPackages.length} / {packages.length} gói dịch vụ
          </span>
        </div>
      )}

      {/* Loading & Error States */}
      {loading ? (
        <p role="status" style={{ textAlign: 'center', padding: '40px 0', color: 'var(--c-text-muted)' }}>
          Đang tải gói dịch vụ…
        </p>
      ) : error ? (
        <div role="alert" className="reception-notice error" style={{ margin: '32px 0' }}>
          {error}
          <Button
            size="sm"
            style={{ marginLeft: 12 }}
            onClick={() => {
              setLoading(true);
              setError('');
              setRevision((v) => v + 1);
            }}
          >
            Thử lại
          </Button>
        </div>
      ) : (
        <>
          {/* Package Cards Grid */}
          <div className="spa-package-grid">
            {filteredPackages.map((pack, index) => {
              const totalSessions = (pack.items || []).reduce(
                (sum, item) => sum + (item.quantity || 0),
                0
              );
              const perSession =
                totalSessions > 0 ? Math.round(pack.price / totalSessions) : null;
              const cardImage = pack.thumbnailUrl
                ? resolveMediaUrl(pack.thumbnailUrl)
                : index % 2 === 0
                ? '/images/spa/facial-care.jpg'
                : '/images/spa/spa-ritual.jpg';

              return (
                <article className="spa-benefit-card" key={pack.id}>
                  {/* Card Thumbnail & Ribbons */}
                  <div className="spa-package-card-thumb">
                    <img
                      src={cardImage}
                      alt={pack.name}
                      loading="lazy"
                      onError={(e) => {
                        if (
                          e.currentTarget.getAttribute('src') !==
                          '/images/spa/facial-care.jpg'
                        ) {
                          e.currentTarget.src = '/images/spa/facial-care.jpg';
                        }
                      }}
                    />
                    <div className="spa-package-ribbon">
                      <Sparkles size={11} /> Gói nhiều lượt
                    </div>
                    <div className="spa-package-validity-pill">
                      <Clock size={12} />
                      {pack.validityDays == null
                        ? 'Không giới hạn hạn dùng'
                        : `${pack.validityDays} ngày từ khi thanh toán`}
                    </div>
                  </div>

                  {/* Card Content Body */}
                  <div className="spa-package-card-body">
                    <h2>{pack.name}</h2>
                    <p className="spa-package-desc">{pack.description}</p>

                    {/* Total Sessions Badge */}
                    <div className="spa-package-total-badge">
                      <Ticket size={13} />
                      <span>Tổng cộng: {totalSessions} buổi dịch vụ</span>
                    </div>

                    {/* Included Services List */}
                    <div className="spa-package-items-wrapper">
                      <div className="spa-package-items-label">
                        Quyền lợi trong gói
                      </div>
                      <ul className="spa-package-items-list">
                        {(pack.items || []).map((item) => (
                          <li
                            key={item.serviceId}
                            className="spa-package-item-row"
                          >
                            <span className="spa-package-item-name">
                              <CheckCircle2 size={14} />
                              <span>{item.serviceName}</span>
                            </span>
                            <span className="spa-package-item-qty">
                              {item.quantity} lượt
                            </span>
                          </li>
                        ))}
                      </ul>
                    </div>

                    {/* Pricing & CTA Button */}
                    <div className="spa-package-card-footer">
                      <div className="spa-package-price-row">
                        <div>
                          <small
                            style={{
                              display: 'block',
                              fontSize: 11,
                              color: 'var(--c-text-light, #6B7280)',
                              textTransform: 'uppercase',
                              letterSpacing: '0.04em',
                              fontWeight: 600
                            }}
                          >
                            Giá trọn gói
                          </small>
                          <strong className="spa-package-price">
                            {money(pack.price)}
                          </strong>
                        </div>
                        {perSession && (
                          <span className="spa-package-per-session">
                            ~{money(perSession)} / buổi
                          </span>
                        )}
                      </div>

                      <Button
                        className="spa-package-buy-btn"
                        onClick={() => selectPackage(pack)}
                      >
                        <Sparkles size={15} /> Xem điều kiện & mua gói
                      </Button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>

          {!filteredPackages.length && (
            <div
              style={{
                textAlign: 'center',
                padding: '60px 20px',
                background: '#FAF7F8',
                borderRadius: 16,
                border: '1px solid var(--c-border, #F0DEE3)',
                margin: '20px 0 40px'
              }}
            >
              <Ticket size={40} color="var(--c-primary, #AD3F61)" style={{ margin: '0 auto 12px' }} />
              <h3 style={{ fontSize: 18, color: 'var(--c-primary-dark, #721B29)', margin: '0 0 6px' }}>
                {searchQuery
                  ? 'Không tìm thấy gói dịch vụ phù hợp'
                  : 'Hiện chưa có gói dịch vụ đang bán'}
              </h3>
              <p style={{ fontSize: 14, color: 'var(--c-text-muted)', margin: 0 }}>
                {searchQuery
                  ? 'Thử thay đổi từ khóa tìm kiếm hoặc bấm xem tất cả.'
                  : 'Vui lòng quay lại sau hoặc liên hệ tư vấn viên để được hỗ trợ.'}
              </p>
              {searchQuery && (
                <Button
                  size="sm"
                  variant="outline"
                  style={{ marginTop: 16 }}
                  onClick={() => setSearchQuery('')}
                >
                  Xóa bộ lọc tìm kiếm
                </Button>
              )}
            </div>
          )}

          {/* Customer Guarantees Bento Section */}
          <section className="spa-package-guarantees" aria-labelledby="spa-guarantees-title">
            <div className="spa-package-guarantees-heading">
              <span className="spa-benefit-eyebrow">QUYỀN LỢI THÀNH VIÊN</span>
              <h3 id="spa-guarantees-title">
                Vì sao nên lựa chọn gói dịch vụ nhiều lượt tại BeautyShop?
              </h3>
              <p>
                Tiêu chuẩn dịch vụ Clinic Spa chuyên nghiệp, chính sách minh bạch
                bảo vệ trọn vẹn quyền lợi của bạn.
              </p>
            </div>

            <div className="spa-package-guarantees-grid">
              <div className="spa-package-guarantee-card">
                <div className="spa-package-guarantee-icon">
                  <Calendar size={22} />
                </div>
                <h4>Tự do chọn lịch từng buổi</h4>
                <p>
                  Bạn không cần thực hiện liên tục. Hãy đặt lịch hẹn bất cứ khi
                  nào thuận tiện qua ứng dụng hoặc website, chọn KTV và phòng
                  riêng tư.
                </p>
              </div>

              <div className="spa-package-guarantee-card">
                <div className="spa-package-guarantee-icon">
                  <Ticket size={22} />
                </div>
                <h4>Ví vé điện tử thông minh</h4>
                <p>
                  Ngay sau khi thanh toán thành công, số lượt dịch vụ được lưu
                  ngay trong hồ sơ tài khoản của bạn, tự động cập nhật sau mỗi
                  lần check-in tại quầy.
                </p>
              </div>

              <div className="spa-package-guarantee-card">
                <div className="spa-package-guarantee-icon">
                  <ShieldCheck size={22} />
                </div>
                <h4>Minh bạch hoàn tiền & gia hạn</h4>
                <p>
                  Mọi biến động số lượt đều có lịch sử rõ ràng. Khách hàng có
                  thể gửi yêu cầu gia hạn khi bận việc, hoặc yêu cầu dừng gói để
                  quyết toán số tiền còn lại.
                </p>
              </div>
            </div>
          </section>
        </>
      )}

      {/* Package Purchase Modal */}
      {selected && (
        <PackagePurchase
          key={selected.id}
          pack={selected}
          authenticated={isAuthenticated}
          onNavigate={onNavigate}
          onClose={() => setSelected(null)}
        />
      )}
    </section>
  );
};

const PackagePurchase = ({ pack, authenticated, onNavigate, onClose }) => {
  const [accepted, setAccepted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const key = useRef(pack.purchaseKey);
  const lock = useRef(false);

  const submit = async (event) => {
    event.preventDefault();
    if (!authenticated || !accepted || lock.current) return;
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      const order = unwrap(
        await apiClient.post(
          '/api/v1/spa/tickets/purchase',
          { packageId: pack.id, purchaseToken: pack.purchaseToken },
          { headers: { 'Idempotency-Key': key.current } }
        )
      );
      if (
        !Number.isSafeInteger(Number(order?.orderId)) ||
        Number(order.orderId) <= 0
      ) {
        throw new Error(
          'Chưa nhận được mã đơn hợp lệ. Kiểm tra Đơn hàng trước khi mua lại.'
        );
      }
      openTreatmentPayment(order);
    } catch (e) {
      setError(
        `${errorMessage(
          e
        )} Thử lại giữ nguyên khóa yêu cầu; bạn có thể kiểm tra Đơn hàng của tôi.`
      );
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };

  return (
    <Modal
      isOpen
      title={`Mua ${pack.name}`}
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form className="spa-benefit-form" onSubmit={submit}>
        <div className="spa-purchase-summary-box">
          <div className="spa-purchase-summary-header">
            <div>
              <span className="spa-benefit-eyebrow">Quyền lợi trọn gói</span>
              <h3
                style={{
                  margin: '4px 0 0',
                  fontSize: '17px',
                  fontWeight: 800,
                  color: 'var(--c-primary-dark, #721B29)'
                }}
              >
                {pack.name}
              </h3>
            </div>
            <strong>{money(pack.price)}</strong>
          </div>
          <p className="spa-purchase-validity-text">
            <Clock
              size={13}
              style={{ display: 'inline', verticalAlign: '-1px', marginRight: 4 }}
            />
            {pack.validityDays == null
              ? 'Không giới hạn hạn dùng.'
              : `Hạn dùng ${pack.validityDays} ngày kể từ thanh toán thành công.`}{' '}
            Vé chỉ cấp sau khi hệ thống xác nhận đủ tiền.
          </p>
          <ul className="spa-purchase-items-list">
            {(pack.items || []).map((s) => (
              <li key={s.serviceId}>
                <span>{s.serviceName}</span>
                <strong>{s.quantity} lượt</strong>
              </li>
            ))}
          </ul>
        </div>

        <div className="spa-purchase-policy-box">
          <p>
            <strong>Chính sách lịch hẹn:</strong> Hủy lịch áp dụng theo chính
            sách lúc đặt; lượt đang giữ chỗ không dùng cho lịch hẹn khác.
          </p>
          <p>
            <strong>Quyết toán & hoàn tiền:</strong> Quyết toán gói cần giải
            phóng các lịch đang giữ; lượt đã dùng hoặc mất theo chính sách không
            được hoàn. Giá trị còn lại phân bổ theo giá dịch vụ tại lúc mua, làm
            tròn xuống nguyên VND; lượt bù không làm tăng tiền hoàn.
          </p>
        </div>

        {!authenticated && (
          <div role="alert" className="spa-purchase-auth-alert">
            <Info size={18} />
            <div style={{ flex: 1 }}>
              Vui lòng đăng nhập bằng nút Tài khoản trước khi mua.
            </div>
            {onNavigate && (
              <Button
                size="sm"
                type="button"
                onClick={() => onNavigate('login')}
              >
                Đăng nhập ngay
              </Button>
            )}
          </div>
        )}

        {error && (
          <p role="alert" style={{ color: '#DC2626', margin: '4px 0', fontSize: 13 }}>
            {error}
          </p>
        )}

        <label className="spa-purchase-terms-label">
          <input
            type="checkbox"
            checked={accepted}
            disabled={busy}
            onChange={(e) => setAccepted(e.target.checked)}
          />
          <span>Tôi xác nhận giá, quyền lợi, hạn dùng và điều kiện trên.</span>
        </label>

        <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
          <Button
            type="button"
            variant="outline"
            disabled={busy}
            onClick={onClose}
            style={{ flex: 1 }}
          >
            Đóng
          </Button>
          <Button
            type="submit"
            loading={busy}
            disabled={!authenticated || !accepted}
            style={{ flex: 2 }}
          >
            Tạo đơn & thanh toán
          </Button>
        </div>
      </form>
    </Modal>
  );
};
