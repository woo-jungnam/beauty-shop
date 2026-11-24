import { useEffect, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { dateLabel, statusLabel, unwrap } from './treatmentApi';
import './customer-appointment-summary.css';

function timeRange(startTime, endTime) {
  return [startTime, endTime].filter(Boolean).map(time => time.slice(0, 5)).join(' – ');
}

export function CustomerAppointmentSummary({ appointment, user }) {
  const [address, setAddress] = useState(null);
  const ownProfile = String(appointment.userId) === String(user?.id) ? user : null;
  const time = timeRange(appointment.startTime, appointment.endTime);

  useEffect(() => {
    const controller = new AbortController();
    apiClient.get('/api/v1/system-configs/public', { signal: controller.signal })
      .then(response => {
        const value = unwrap(response)?.['store.address'];
        if (!controller.signal.aborted) setAddress(typeof value === 'string' ? value.trim() : '');
      })
      .catch(() => {
        if (!controller.signal.aborted) setAddress('');
      });
    return () => controller.abort();
  }, []);

  return (
    <section className="customer-appointment-summary treatment-form-session">
      <div className="treatment-card-heading">
        <h3>Thông tin buổi hẹn</h3>
        <span className="treatment-badge">{statusLabel(appointment.status)}</span>
      </div>
      <dl className="customer-appointment-fields">
        <div><dt>Khách hàng</dt><dd>{appointment.customerName?.trim() || ownProfile?.fullName || 'Chưa cập nhật'}</dd></div>
        <div><dt>Số điện thoại</dt><dd>{appointment.customerPhone?.trim() || ownProfile?.phone || 'Chưa cập nhật'}</dd></div>
        <div><dt>Ngày hẹn</dt><dd>{appointment.appointmentDate ? dateLabel(appointment.appointmentDate) : 'Chưa xác định'}</dd></div>
        <div><dt>Giờ hẹn (giờ Việt Nam)</dt><dd>{time || 'Chưa xác định'}</dd></div>
        <div className="customer-appointment-location">
          <dt>Địa điểm</dt>
          <dd>BeautyShop Spa</dd>
          <dd>{address
            ? <a className="treatment-link" href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`} target="_blank" rel="noopener noreferrer">{address}</a>
            : address === null ? 'Đang tải địa chỉ…' : 'Chưa có địa chỉ'}</dd>
        </div>
      </dl>
      <h3>Dịch vụ đã đặt</h3>
      {appointment.items?.length ? (
        <ul className="customer-appointment-services">
          {appointment.items.map(item => {
            const serviceTime = timeRange(item.startTime, item.endTime);
            return (
              <li key={item.id}>
                <strong>{item.serviceName || 'Dịch vụ Spa'}</strong>
                <span>{serviceTime && <time>{serviceTime}</time>}{serviceTime && ' · '}{item.staffName && item.staffName !== 'No preference' ? item.staffName : 'Spa sắp xếp chuyên viên'}</span>
              </li>
            );
          })}
        </ul>
      ) : <p className="treatment-muted">Chưa có thông tin dịch vụ.</p>}
    </section>
  );
}
