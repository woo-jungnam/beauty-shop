import React, { useEffect, useRef, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { useAuth } from '../../app/providers/AuthProvider';
import { Button } from '../../shared/ui/Button';
import { Modal } from '../../shared/ui/Modal';
import { money, unwrap, errorMessage, idempotencyKey } from '../treatments/treatmentApi';
import { openTreatmentPayment } from '../treatments/paymentNavigation';
import './spa-benefits.css';

export const ServicePackages = ({ onNavigate }) => {
  const { isAuthenticated, user } = useAuth();
  const purchaseKeys = useRef(new Map());
  useEffect(() => { purchaseKeys.current.clear(); }, [user?.id]);
  const [packages, setPackages] = useState([]), [error, setError] = useState(''), [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null), [revision, setRevision] = useState(0);
  const selectPackage = pack => {
    if (!purchaseKeys.current.has(pack.id)) purchaseKeys.current.set(pack.id, idempotencyKey());
    setSelected({ ...pack, purchaseKey: purchaseKeys.current.get(pack.id) });
  };
  useEffect(() => {
    let active = true;
    apiClient.get('/api/v1/spa/services/packages').then(r => { const data = unwrap(r); if (!Array.isArray(data)) throw new Error('Dữ liệu gói không hợp lệ'); if (active) setPackages(data); })
      .catch(e => { if (active) setError(errorMessage(e)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [revision]);
  return <section className="spa-benefits"><h1>Gói dịch vụ nhiều lượt</h1><p>Mua trước quyền sử dụng từng dịch vụ. Lộ trình điều trị có kế hoạch riêng tại mục Điều trị.</p>
    <div className="spa-benefit-actions"><Button variant="outline" onClick={() => onNavigate('booking')}>Đặt dịch vụ lẻ / dùng vé</Button><Button variant="outline" onClick={() => onNavigate('profile?tab=tickets')}>Vé của tôi</Button></div>
    {loading ? <p role="status">Đang tải gói dịch vụ…</p> : error ? <div role="alert">{error}<Button onClick={() => { setLoading(true); setError(''); setRevision(v => v + 1); }}>Thử lại</Button></div> : <div className="spa-package-grid">{packages.map(pack => <article className="spa-benefit-card" key={pack.id}>
      <h2>{pack.name}</h2><p>{pack.description}</p><strong>{money(pack.price)}</strong><p>{pack.validityDays == null ? 'Không giới hạn hạn dùng' : `${pack.validityDays} ngày từ khi thanh toán`}</p>
      <ul>{pack.items.map(item => <li key={item.serviceId}>{item.serviceName}: <strong>{item.quantity} lượt</strong></li>)}</ul>
      <Button onClick={() => selectPackage(pack)}>Xem điều kiện & mua gói</Button></article>)}{!packages.length && <p>Hiện chưa có gói dịch vụ đang bán.</p>}</div>}
    {selected && <PackagePurchase key={selected.id} pack={selected} authenticated={isAuthenticated} onClose={() => setSelected(null)} />}
  </section>;
};
const PackagePurchase = ({ pack, authenticated, onClose }) => {
  const [accepted, setAccepted] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const key = useRef(pack.purchaseKey), lock = useRef(false);
  const submit = async event => {
    event.preventDefault(); if (!authenticated || !accepted || lock.current) return; lock.current = true; setBusy(true); setError('');
    try {
      const order = unwrap(await apiClient.post('/api/v1/spa/tickets/purchase', { packageId: pack.id, purchaseToken: pack.purchaseToken }, { headers: { 'Idempotency-Key': key.current } }));
      if (!Number.isSafeInteger(Number(order?.orderId)) || Number(order.orderId) <= 0) throw new Error('Chưa nhận được mã đơn hợp lệ. Kiểm tra Đơn hàng trước khi mua lại.');
      openTreatmentPayment(order);
    } catch (e) { setError(`${errorMessage(e)} Thử lại giữ nguyên khóa yêu cầu; bạn có thể kiểm tra Đơn hàng của tôi.`); }
    finally { lock.current = false; setBusy(false); }
  };
  return <Modal isOpen title={`Mua ${pack.name}`} onClose={() => { if (!busy) onClose(); }}><form className="spa-benefit-form" onSubmit={submit}>
    <strong>{money(pack.price)}</strong><ul>{pack.items.map(s => <li key={s.serviceId}>{s.serviceName} — {s.quantity} lượt</li>)}</ul>
    <p>{pack.validityDays == null ? 'Không giới hạn hạn dùng.' : `Hạn dùng ${pack.validityDays} ngày kể từ thanh toán thành công.`} Vé chỉ cấp sau khi hệ thống xác nhận đủ tiền.</p>
    <p>Hủy lịch áp dụng chính sách lúc đặt; lượt đang giữ không dùng cho lịch khác. Quyết toán gói cần giải phóng lịch đang giữ; lượt đã dùng hoặc mất theo chính sách không được hoàn. Giá trị còn lại phân bổ theo giá dịch vụ tại lúc mua, làm tròn xuống nguyên VND; lượt bù không làm tăng tiền hoàn.</p>
    {!authenticated && <p role="alert">Vui lòng đăng nhập bằng nút Tài khoản trước khi mua.</p>}
    {error && <p role="alert">{error}</p>}<label><input type="checkbox" checked={accepted} disabled={busy} onChange={e => setAccepted(e.target.checked)} /> Tôi xác nhận giá, quyền lợi, hạn dùng và điều kiện trên.</label>
    <Button type="submit" loading={busy} disabled={!authenticated || !accepted}>Tạo đơn & thanh toán</Button>
  </form></Modal>;
};
