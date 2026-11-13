import React, { useEffect, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { formatCurrency } from '../../shared/utils/formatters';
import { Button } from '../../shared/ui/Button';
import { DataTable } from '../../shared/ui/DataTable';
import { Modal } from '../../shared/ui/Modal';
import { AppointmentFinance } from '../treatments/AppointmentFinance';

const statusLabels = { PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', IN_PROGRESS: 'Đang thực hiện', COMPLETED: 'Đã hoàn thành', CANCELLED: 'Đã hủy', NO_SHOW: 'Vắng hẹn' };

export function ServiceBookingOrders({ admin = false, refreshKey = 0, onNavigate }) {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [selected, setSelected] = useState(null);
  useEffect(() => {
    let current = true;
    const controller = new AbortController();
    setLoading(true); setError('');
    apiClient.get(`/api/v1/appointments/${admin ? 'admin/service-orders' : 'my-service-orders'}?page=${page}&size=10`, { signal: controller.signal })
      .then(response => { if (current) setResult(response?.data ?? response); })
      .catch(failure => { if (current) { setError(failure.message || 'Không tải được dịch vụ đã đặt.'); setResult(null); } })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; controller.abort(); };
  }, [admin, page, refresh, refreshKey]);

  useEffect(() => {
    if (result && result.totalPages > 0 && page >= result.totalPages) {
      setPage(Math.max(0, result.totalPages - 1));
    }
  }, [result, page]);

  const columns = [
    { header: 'Mã đặt dịch vụ', accessor: row => `LH-${row.appointmentId}` },
    ...(admin ? [{ header: 'Khách hàng', accessor: row => row.customerName || '—' }] : []),
    { header: 'Dịch vụ đơn', accessor: row => (row.services || []).join(', ') },
    { header: 'Lịch hẹn', accessor: row => `${row.appointmentDate?.split('-').reverse().join('/')} · ${row.startTime?.slice(0, 5) || '—'}` },
    { header: 'Trạng thái', accessor: row => statusLabels[row.status] || row.status },
    { header: 'Giá dự kiến', accessor: row => formatCurrency(row.estimatedAmount), align: 'right' },
    { header: 'Đã thu', accessor: row => formatCurrency(row.collectedAmount), align: 'right' },
    { header: 'Đã hoàn', accessor: row => formatCurrency(row.refundedAmount), align: 'right' },
    { header: 'Chi tiết', accessor: row => <Button size="sm" variant="outline" onClick={() => setSelected(row)}>Xem dịch vụ</Button> },
  ];
  return <section className="card" aria-label="Dịch vụ đơn đã đặt" style={{ padding: 20, margin: '20px 0' }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
      <h3>Dịch vụ đơn đã đặt</h3>
      <Button size="sm" variant="outline" loading={loading} onClick={() => setRefresh(value => value + 1)}>Cập nhật dịch vụ</Button>
    </div>
    <p>Đơn đặt dịch vụ chưa lập hóa đơn. Giá cuối cùng được chốt theo dịch vụ thực hiện; sau khi lập hóa đơn, thông tin chuyển sang danh sách đơn hàng bên dưới.</p>
    {error ? <p role="alert">{error}</p> : <DataTable columns={columns} data={result?.content || []} loading={loading}
      page={page} pageSize={10} totalPages={result?.totalPages || 1} totalElements={result?.totalElements || 0}
      onPageChange={setPage} emptyMessage="Chưa có dịch vụ đơn chờ lập hóa đơn." />}
    {selected && <Modal isOpen title={`Đơn đặt dịch vụ LH-${selected.appointmentId}`} onClose={() => setSelected(null)}>
      <p>{selected.services?.join(', ')}</p>
      <p>{selected.appointmentDate?.split('-').reverse().join('/')} · {selected.startTime?.slice(0, 5)} · {statusLabels[selected.status] || selected.status}</p>
      <AppointmentFinance appointmentId={selected.appointmentId} role={admin ? 'admin' : 'customer'} defaultOpen />
      {!admin && <Button variant="outline" onClick={() => {
        const destination = `profile?tab=appointments&appointment=${selected.appointmentId}`;
        if (onNavigate) onNavigate(destination); else window.location.hash = `#/${destination}`;
      }}>Mở lịch hẹn</Button>}
    </Modal>}
  </section>;
}
