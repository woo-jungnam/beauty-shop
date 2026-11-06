import React, { useEffect, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { unwrap } from '../treatments/treatmentApi';

export const BookingPolicyNotice = ({ snapshot }) => {
  const [policy, setPolicy] = useState(null);
  useEffect(() => {
    if (snapshot !== undefined) {
      try { setPolicy(typeof snapshot === 'string' ? JSON.parse(snapshot) : snapshot); } catch { setPolicy(null); }
      return;
    }
    let active = true;
    apiClient.get('/api/v1/spa/booking-policy').then(r => { if (active) setPolicy(unwrap(r)); }).catch(() => { if (active) setPolicy(null); });
    return () => { active = false; };
  }, [snapshot]);
  if (!policy || typeof policy.cancelCutoffMinutes !== 'number') return null;
  return <p className="treatment-muted">Chính sách lịch hẹn: đổi trước ít nhất {policy.rescheduleCutoffMinutes} phút, hủy trước ít nhất {policy.cancelCutoffMinutes} phút.
    {policy.maxReschedules > 0 ? ` Khách được đổi tối đa ${policy.maxReschedules} lần mỗi lịch.` : ''}
    {policy.maxAdvanceDays > 0 ? ` Đặt trước tối đa ${policy.maxAdvanceDays} ngày.` : ''}
    {policy.pendingTtlMinutes > 0 ? ` Lịch chờ xác nhận có hạn ${policy.pendingTtlMinutes} phút; nếu cần cọc, làm theo hạn thanh toán hiển thị.` : ''}
    {` Vắng hẹn sau ${policy.noShowGraceMinutes} phút: ${policy.noShowQuotaAction === 'FORFEIT' ? 'trừ lượt đã giữ' : 'trả lượt đã giữ'}.`}
  </p>;
};
