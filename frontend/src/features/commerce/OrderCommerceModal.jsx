import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Camera,
  CheckCircle2,
  DollarSign,
  ExternalLink,
  PackageCheck,
  RefreshCw,
  RotateCcw,
  Ticket,
  Truck,
  XCircle,
} from 'lucide-react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { useAuth } from '../../app/providers/AuthProvider';
import {
  formatCurrency,
  formatDateTime,
  getOrderStatusBadge,
  getOrderType,
  getPaymentMethodLabel,
  getPaymentStatusBadge,
  getReturnMethodLabel,
  getReturnStatusBadge,
  resolveMediaUrl,
} from '../../shared/utils/formatters';
import { buildVietQrUrl } from '../../shared/utils/vietnamBanks';
import { AlertBanner } from '../../shared/ui/AlertBanner';
import { Badge } from '../../shared/ui/Badge';
import { Button } from '../../shared/ui/Button';
import { Input } from '../../shared/ui/Input';
import { Modal } from '../../shared/ui/Modal';
import { Select } from '../../shared/ui/Select';

import {
  WHOLE_ORDER_RETURN_STATUSES,
  buildOrderItemMap,
  formatReturnLineLabel,
} from './orderCommerceHelpers';

const unwrap = (response) =>
  response && typeof response === 'object' && 'data' in response ? response.data : response;

const createDocumentRef = (prefix, orderId, suffix = '') =>
  `${prefix}-${orderId}${suffix ? `-${suffix}` : ''}-${Date.now().toString().slice(-5)}`;

function ReturnRequestCard({ ret, orderDetail, itemMap, isAdmin, onCompleted }) {
  const [mode, setMode] = useState('');
  const [reason, setReason] = useState('');
  const [reference, setReference] = useState('');
  const [replacementOrderId, setReplacementOrderId] = useState('');
  const [decisionReturnMethod, setDecisionReturnMethod] = useState(ret.returnMethod || 'SELF_SHIP');
  const [pickupShipperId, setPickupShipperId] = useState(ret.pickupShipperId ? String(ret.pickupShipperId) : '');
  const [shippers, setShippers] = useState([]);
  const [shippersLoading, setShippersLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const statusBadge = getReturnStatusBadge(ret.status);
  const isVoucher = ret.refundMethod === 'VOUCHER';
  const isShipperPickup = ret.returnMethod === 'SHIPPER_PICKUP';
  const vietQrUrl = useMemo(() => {
    if (isVoucher || !ret.bankBin || !ret.accountNumber) return '';
    return buildVietQrUrl({
      bankBin: ret.bankBin,
      accountNumber: ret.accountNumber,
      accountName: ret.accountName,
      amount: ret.refundAmount,
      addInfo: `Hoan tien don ${orderDetail.orderNumber || orderDetail.id}`,
    });
  }, [isVoucher, ret, orderDetail.orderNumber, orderDetail.id]);

  useEffect(() => {
    if (mode !== 'assign-pickup') return undefined;
    let active = true;
    setShippersLoading(true);
    apiClient
      .get(`${ENDPOINTS.USERS.SHIPPERS}?page=0&size=50&sort=id,asc`)
      .then((res) => {
        if (!active) return;
        const pageData = unwrap(res);
        const list = Array.isArray(pageData?.content) ? pageData.content : [];
        setShippers(list);
        if (!pickupShipperId && list.length === 1) {
          setPickupShipperId(String(list[0].id));
        }
      })
      .catch((err) => {
        if (active) setError(err.message || 'Không tải được danh sách tài xế.');
      })
      .finally(() => {
        if (active) setShippersLoading(false);
      });
    return () => {
      active = false;
    };
  }, [mode, pickupShipperId]);

  const openMode = (nextMode) => {
    setError('');
    setMode(nextMode);
    if (nextMode === 'approve') {
      setDecisionReturnMethod(ret.returnMethod || 'SELF_SHIP');
      setReason('Đáp ứng điều kiện đổi trả theo chính sách cửa hàng');
    } else if (nextMode === 'reject') {
      setReason('');
    } else if (nextMode === 'assign-pickup') {
      setPickupShipperId(ret.pickupShipperId ? String(ret.pickupShipperId) : '');
      setReason('');
    } else if (nextMode === 'receive') {
      setReference(createDocumentRef('RCV', orderDetail.id, ret.id));
      setReason('Đã kiểm nhận hàng hoàn về kho cách ly');
    } else if (nextMode === 'refund') {
      setReference(isVoucher ? '' : createDocumentRef('REF', orderDetail.id, ret.id));
      setReason(isVoucher ? 'Cấp voucher hoàn tiền cá nhân cho khách hàng' : 'Đã chuyển khoản hoàn tiền cho khách hàng');
    } else if (nextMode === 'replacement') {
      setReplacementOrderId('');
      setReason('Khách hàng đặt đơn thay thế tương ứng');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      let endpoint = '';
      let payload = {};
      if (mode === 'approve' || mode === 'reject') {
        endpoint = ENDPOINTS.ORDERS.COMMERCE_RETURN_DECISION(ret.id);
        payload = {
          approved: mode === 'approve',
          reason: reason.trim(),
          ...(mode === 'approve' ? { returnMethod: decisionReturnMethod } : {}),
        };
      } else if (mode === 'assign-pickup') {
        endpoint = ENDPOINTS.ORDERS.COMMERCE_RETURN_ASSIGN_PICKUP(ret.id);
        payload = { shipperId: Number(pickupShipperId), notes: reason.trim() || null };
      } else if (mode === 'receive') {
        endpoint = ENDPOINTS.ORDERS.COMMERCE_RETURN_RECEIVE(ret.id);
        payload = { reference: reference.trim(), reason: reason.trim() };
      } else if (mode === 'refund') {
        endpoint = ENDPOINTS.ORDERS.COMMERCE_RETURN_REFUND(ret.id);
        payload = {
          reference: isVoucher ? reference.trim() || null : reference.trim(),
          reason: reason.trim(),
        };
      } else if (mode === 'replacement') {
        endpoint = ENDPOINTS.ORDERS.COMMERCE_RETURN_REPLACEMENT(ret.id);
        payload = { replacementOrderId: Number(replacementOrderId), reason: reason.trim() };
      }
      await apiClient.post(endpoint, payload);
      setMode('');
      await onCompleted?.('Đã cập nhật xử lý yêu cầu đổi trả thành công.');
    } catch (err) {
      setError(err.message || 'Không thể xử lý yêu cầu đổi trả.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      style={{
        border: '1px solid var(--border-subtle)',
        borderRadius: '10px',
        padding: '14px',
        background: '#FFFFFF',
        display: 'flex',
        flexDirection: 'column',
        gap: '10px',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <strong style={{ fontSize: '13.5px' }}>Yêu cầu #{ret.id}</strong>
          <Badge variant={statusBadge.variant}>{statusBadge.text}</Badge>
          <Badge variant={isShipperPickup ? 'info' : 'neutral'}>{getReturnMethodLabel(ret.returnMethod)}</Badge>
          {ret.createdAt && (
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{formatDateTime(ret.createdAt)}</span>
          )}
        </div>
        <div style={{ textAlign: 'right' }}>
          <span style={{ fontSize: '12px', color: 'var(--text-muted)', marginRight: '6px' }}>Giá trị hoàn:</span>
          <strong style={{ fontSize: '14px', color: '#B45309' }}>{formatCurrency(ret.refundAmount)}</strong>
        </div>
      </div>

      <div style={{ fontSize: '13px', background: '#F8FAFC', borderRadius: '8px', padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <div style={{ fontWeight: 600, color: '#334155' }}>Sản phẩm yêu cầu trả:</div>
        {(ret.lines || []).map((line, idx) => (
          <div key={`${line.orderItemId}-${idx}`} style={{ color: '#0F172A' }}>
            • {formatReturnLineLabel(line, itemMap)}
          </div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '10px', fontSize: '12.5px' }}>
        <div>
          <div style={{ color: 'var(--text-muted)', marginBottom: '2px' }}>Lý do khách hàng:</div>
          <div style={{ fontWeight: 500 }}>{ret.reason || '—'}</div>
          {ret.decisionReason && (
            <div style={{ color: '#475569', marginTop: '4px' }}>
              <strong>Ghi chú duyệt:</strong> {ret.decisionReason}
            </div>
          )}
        </div>

        <div>
          <div style={{ color: 'var(--text-muted)', marginBottom: '2px' }}>Kênh nhận hoàn tiền:</div>
          {isVoucher ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
              <Badge variant="info" icon={Ticket}>Voucher hoàn tiền cá nhân</Badge>
              {ret.refundVoucherCode && (
                <code style={{ background: '#ECFDF5', color: '#047857', padding: '2px 8px', borderRadius: '4px', fontWeight: 700 }}>
                  {ret.refundVoucherCode}
                </code>
              )}
            </div>
          ) : (
            <div>
              <div style={{ fontWeight: 600 }}>Chuyển khoản ngân hàng</div>
              <div style={{ color: '#334155', marginTop: '2px' }}>
                {ret.bankName || ret.bankBin || 'Chưa rõ NH'} · STK: <code>{ret.accountNumber || '—'}</code>
              </div>
              {ret.accountName && <div style={{ color: '#475569' }}>Chủ TK: {ret.accountName}</div>}
            </div>
          )}
        </div>
      </div>

      {(isShipperPickup || ret.pickupShipperName || ret.pickupFailureReason || ret.pickupProofUrl) && (
        <div style={{ background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: '8px', padding: '10px 12px', fontSize: '12.5px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '6px' }}>
            <strong style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', color: '#1E293B' }}>
              <Truck size={14} />
              <span>Thông tin thu hồi hàng hoàn</span>
            </strong>
            {ret.handoverRequestedAt && ret.status === 'PICKED_UP' && (
              <Badge variant="warning">Shipper đã báo bàn giao về kho · Chờ kiểm nhận</Badge>
            )}
          </div>
          {ret.pickupShipperName ? (
            <div style={{ color: '#334155' }}>
              Tài xế: <strong>{ret.pickupShipperName}</strong>
              {ret.pickupShipperPhone ? ` (${ret.pickupShipperPhone})` : ''}
              {ret.pickupAssignedAt ? ` · Giao lúc ${formatDateTime(ret.pickupAssignedAt)}` : ''}
              {ret.pickupStartedAt ? ` · Bắt đầu lấy ${formatDateTime(ret.pickupStartedAt)}` : ''}
              {ret.pickedUpAt ? ` · Đã lấy ${formatDateTime(ret.pickedUpAt)}` : ''}
            </div>
          ) : (
            isShipperPickup && ret.status === 'APPROVED' && (
              <div style={{ color: '#92400E' }}>Chưa phân công tài xế đến lấy hàng hoàn.</div>
            )
          )}
          {ret.pickupFailureReason && (
            <div style={{ color: '#B91C1C', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: '6px', padding: '6px 8px' }}>
              <strong>Thu hồi thất bại (lần {ret.pickupAttemptCount}):</strong> {ret.pickupFailureReason}
            </div>
          )}
          {ret.pickupNotes && (
            <div style={{ color: '#334155' }}>
              <strong>Ghi chú lúc lấy hàng:</strong> {ret.pickupNotes}
            </div>
          )}
          {ret.handoverRequestedAt && (
            <div style={{ color: '#0369A1' }}>
              <strong>Shipper báo bàn giao kho:</strong> {formatDateTime(ret.handoverRequestedAt)}
              {ret.handoverNotes ? ` · ${ret.handoverNotes}` : ''}
            </div>
          )}
          {ret.pickupProofUrl && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '2px', flexWrap: 'wrap' }}>
              <a href={resolveMediaUrl(ret.pickupProofUrl)} target="_blank" rel="noreferrer" title="Mở ảnh shipper lấy hàng">
                <img
                  src={resolveMediaUrl(ret.pickupProofUrl)}
                  alt="Ảnh xác nhận lấy hàng hoàn"
                  style={{ width: '64px', height: '64px', objectFit: 'cover', borderRadius: '6px', border: '1px solid #CBD5E1' }}
                />
              </a>
              <div style={{ fontSize: '12px', color: '#475569' }}>
                <div style={{ fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <Camera size={13} /> Ảnh xác nhận tài xế đã nhận hàng hoàn
                </div>
                <div>
                  <a href={resolveMediaUrl(ret.pickupProofUrl)} target="_blank" rel="noreferrer" style={{ color: '#2563EB' }}>
                    Xem ảnh gốc <ExternalLink size={11} />
                  </a>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {ret.evidenceUrls?.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
          <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Ảnh chứng cứ:</span>
          {ret.evidenceUrls.map((url, idx) => (
            <a key={`${url}-${idx}`} href={resolveMediaUrl(url)} target="_blank" rel="noreferrer" title={`Mở ảnh chứng cứ ${idx + 1}`}>
              <img
                src={resolveMediaUrl(url)}
                alt={`Chứng cứ trả hàng ${idx + 1}`}
                style={{ width: '54px', height: '54px', objectFit: 'cover', borderRadius: '6px', border: '1px solid var(--border-subtle)' }}
              />
            </a>
          ))}
        </div>
      )}

      {ret.replacementOrderId && (
        <div style={{ fontSize: '12.5px', padding: '8px 10px', borderRadius: '6px', background: '#EFF6FF', color: '#1E40AF' }}>
          Đã liên kết đơn thay thế <strong>#{ret.replacementOrderId}</strong>
          {ret.replacementReason ? ` · ${ret.replacementReason}` : ''}
        </div>
      )}

      {ret.status === 'RECEIVED' && vietQrUrl && isAdmin && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '10px', background: '#F0FDF4', border: '1px solid #BBF7D0', borderRadius: '8px', flexWrap: 'wrap' }}>
          <img src={vietQrUrl} alt="Mã VietQR hoàn tiền" style={{ width: '112px', height: '112px', borderRadius: '6px', border: '1px solid #DCFCE7', background: '#FFFFFF' }} />
          <div style={{ fontSize: '12.5px', color: '#166534' }}>
            <div style={{ fontWeight: 700, marginBottom: '4px' }}>Quét mã VietQR để chuyển khoản hoàn tiền</div>
            <div>Ngân hàng: {ret.bankName || ret.bankBin}</div>
            <div>Số tài khoản: <strong>{ret.accountNumber}</strong> · {ret.accountName}</div>
            <div>Số tiền: <strong>{formatCurrency(ret.refundAmount)}</strong></div>
            <a href={vietQrUrl} target="_blank" rel="noreferrer" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', marginTop: '4px', color: '#15803D', fontWeight: 600 }}>
              Mở ảnh QR lớn <ExternalLink size={12} />
            </a>
          </div>
        </div>
      )}

      {!mode && (
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', paddingTop: '4px', alignItems: 'center' }}>
          {ret.status === 'REQUESTED' && (
            <>
              <Button size="sm" variant="primary" icon={CheckCircle2} onClick={() => openMode('approve')}>
                Chấp thuận yêu cầu
              </Button>
              <Button size="sm" variant="danger" icon={XCircle} onClick={() => openMode('reject')}>
                Từ chối yêu cầu
              </Button>
            </>
          )}
          {ret.status === 'APPROVED' && isShipperPickup && (
            <Button size="sm" variant="primary" icon={Truck} onClick={() => openMode('assign-pickup')}>
              Điều phối shipper thu hồi
            </Button>
          )}
          {ret.status === 'APPROVED' && !isShipperPickup && (
            <Button size="sm" variant="primary" icon={PackageCheck} onClick={() => openMode('receive')}>
              Xác nhận đã nhận hàng trả
            </Button>
          )}
          {ret.status === 'PICKUP_ASSIGNED' && (
            <>
              {!ret.pickupStartedAt && (
                <Button size="sm" variant="outline" icon={Truck} onClick={() => openMode('assign-pickup')}>
                  Đổi shipper thu hồi
                </Button>
              )}
              <span style={{ fontSize: '12px', color: '#1D4ED8' }}>
                {ret.pickupStartedAt
                  ? 'Tài xế đang trên đường đi lấy hàng hoàn từ khách.'
                  : 'Đang chờ tài xế nhận đơn và chụp ảnh thu hồi trên ứng dụng BeautyShip.'}
              </span>
            </>
          )}
          {ret.status === 'PICKED_UP' && (
            <Button size="sm" variant="primary" icon={PackageCheck} onClick={() => openMode('receive')}>
              Xác nhận kho đã kiểm nhận hàng
            </Button>
          )}
          {ret.status === 'RECEIVED' && (
            isAdmin ? (
              <Button size="sm" variant="primary" icon={DollarSign} onClick={() => openMode('refund')}>
                {isVoucher ? 'Xác nhận cấp Voucher hoàn tiền' : 'Xác nhận đã hoàn tiền'}
              </Button>
            ) : (
              <span style={{ fontSize: '12px', color: '#B45309' }}>
                Yêu cầu cần Quản trị viên (ADMIN) xác nhận hoàn tiền.
              </span>
            )
          )}
          {ret.status === 'REFUNDED' && !ret.replacementOrderId && (
            <Button size="sm" variant="outline" onClick={() => openMode('replacement')}>
              Liên kết đơn thay thế (tùy chọn)
            </Button>
          )}
        </div>
      )}

      {mode && (
        <form onSubmit={handleSubmit} style={{ borderTop: '1px dashed var(--border-subtle)', paddingTop: '10px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {error && <AlertBanner type="danger" message={error} />}

          {mode === 'approve' && (
            <Select
              label="Hình thức thu hồi hàng hoàn"
              value={decisionReturnMethod}
              onChange={(e) => setDecisionReturnMethod(e.target.value)}
            >
              <option value="SHIPPER_PICKUP">Shipper đến lấy tận nơi (nội bộ)</option>
              <option value="SELF_SHIP">Khách tự gửi về cửa hàng BeautyShop</option>
            </Select>
          )}

          {mode === 'assign-pickup' && (
            <Select
              label="Chọn tài xế thu hồi hàng hoàn *"
              value={pickupShipperId}
              onChange={(e) => setPickupShipperId(e.target.value)}
              disabled={shippersLoading || !shippers.length}
              required
            >
              <option value="" disabled>
                {shippersLoading ? 'Đang tải danh sách tài xế…' : shippers.length ? 'Chọn tài xế' : 'Không có tài xế khả dụng'}
              </option>
              {shippers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.fullName || s.username} · {s.username}{s.phone ? ` · ${s.phone}` : ''}
                </option>
              ))}
            </Select>
          )}

          {(mode === 'receive' || (mode === 'refund' && !isVoucher)) && (
            <Input
              label={mode === 'receive' ? 'Mã phiếu nhập / kiểm hàng trả' : 'Mã giao dịch hoàn tiền'}
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              maxLength={100}
              required
            />
          )}

          {mode === 'refund' && isVoucher && (
            <Input
              label="Mã tham chiếu (tùy chọn, hệ thống tự cấp mã Voucher)"
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              maxLength={100}
              placeholder="Để trống nếu muốn hệ thống tự sinh mã"
            />
          )}

          {mode === 'replacement' && (
            <Input
              label="ID đơn hàng thay thế (cùng khách hàng)"
              type="number"
              min="1"
              value={replacementOrderId}
              onChange={(e) => setReplacementOrderId(e.target.value)}
              required
            />
          )}

          <Input
            label={
              mode === 'reject'
                ? 'Lý do từ chối yêu cầu'
                : mode === 'assign-pickup'
                ? 'Ghi chú cho tài xế thu hồi (tùy chọn)'
                : 'Lý do / Ghi chú xác nhận'
            }
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={500}
            required={mode !== 'assign-pickup'}
          />

          <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
            <Button type="button" size="sm" variant="outline" onClick={() => setMode('')} disabled={busy}>
              Hủy
            </Button>
            <Button
              type="submit"
              size="sm"
              variant={mode === 'reject' ? 'danger' : 'primary'}
              loading={busy}
              disabled={mode === 'assign-pickup' && (shippersLoading || !pickupShipperId)}
            >
              {mode === 'assign-pickup' ? 'Phân công thu hồi' : 'Lưu xác nhận'}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}

export function OrderCommerceModal({ order, isOpen, onClose, onChanged, onOpenWholeRefund }) {
  const { isAdmin } = useAuth();
  const [detail, setDetail] = useState(null);
  const [returns, setReturns] = useState([]);
  const [balances, setBalances] = useState(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [feedback, setFeedback] = useState(null);

  const [codAmount, setCodAmount] = useState('');
  const [codRef, setCodRef] = useState('');
  const [codReason, setCodReason] = useState('');
  const [overpayAmount, setOverpayAmount] = useState('');
  const [overpayRef, setOverpayRef] = useState('');
  const [overpayReason, setOverpayReason] = useState('');
  const [opBusy, setOpBusy] = useState('');
  const orderId = order?.id;

  const loadOrderCommerce = useCallback(async () => {
    if (!orderId) return;
    setLoading(true);
    setLoadError('');
    try {
      const [detailRes, returnsRes, balancesRes] = await Promise.all([
        apiClient.get(ENDPOINTS.ORDERS.ADMIN_DETAIL(orderId)),
        apiClient.get(ENDPOINTS.ORDERS.COMMERCE_RETURNS(orderId)),
        apiClient.get(ENDPOINTS.ORDERS.COMMERCE_BALANCES(orderId)),
      ]);
      const freshDetail = unwrap(detailRes);
      const freshReturns = unwrap(returnsRes) || [];
      const freshBalances = unwrap(balancesRes) || null;
      setDetail(freshDetail);
      setReturns(Array.isArray(freshReturns) ? freshReturns : []);
      setBalances(freshBalances);
      setCodAmount(Number(freshBalances?.unsettledCod || 0) > 0 ? String(Math.round(freshBalances.unsettledCod)) : '');
      setCodRef(createDocumentRef('COD', orderId));
      setCodReason('Đối soát nhận tiền COD từ đơn vị vận chuyển');
      setOverpayAmount(Number(freshBalances?.remainingOverpayment || 0) > 0 ? String(Math.round(freshBalances.remainingOverpayment)) : '');
      setOverpayRef(createDocumentRef('OVR', orderId));
      setOverpayReason('Hoàn lại khoản khách chuyển thừa');
    } catch (err) {
      setLoadError(err.message || 'Không tải được dữ liệu hoàn hàng / hoàn tiền của đơn.');
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    if (!isOpen || !orderId) return undefined;
    let cancelled = false;
    (async () => {
      await Promise.resolve();
      if (cancelled) return;
      setFeedback(null);
      await loadOrderCommerce();
    })();
    return () => {
      cancelled = true;
    };
  }, [isOpen, orderId, loadOrderCommerce]);

  const activeOrder = detail || order;
  const itemMap = useMemo(() => buildOrderItemMap(activeOrder?.items), [activeOrder?.items]);

  const handleMutated = async (successMsg) => {
    setFeedback({ type: 'success', message: successMsg });
    await loadOrderCommerce();
    onChanged?.();
  };

  const runOrderOp = async (opKey, runner, successMsg) => {
    setOpBusy(opKey);
    setFeedback(null);
    try {
      await runner();
      await handleMutated(successMsg);
    } catch (err) {
      setFeedback({ type: 'danger', message: err.message || 'Không thể thực hiện nghiệp vụ trên đơn hàng.' });
    } finally {
      setOpBusy('');
    }
  };

  const orderStatusBadge = getOrderStatusBadge(activeOrder?.status);
  const payBadge = getPaymentStatusBadge(activeOrder?.paymentStatus);
  const hasNonRejectedReturns = returns.some((r) => r.status !== 'REJECTED');
  const paidMinusRefunded = Math.max(
    0,
    Number(balances?.paidAmount ?? activeOrder?.paidAmount ?? 0) -
      Number(balances?.refundedAmount ?? activeOrder?.refundedAmount ?? 0)
  );
  const showWholeRefundCard =
    activeOrder &&
    activeOrder.paymentStatus !== 'REFUNDED' &&
    (WHOLE_ORDER_RETURN_STATUSES.includes(activeOrder.status) ||
      activeOrder.paymentStatus === 'REFUND_PENDING' ||
      hasNonRejectedReturns);
  const canWholeOrderRefund =
    isAdmin &&
    !hasNonRejectedReturns &&
    paidMinusRefunded > 0 &&
    (WHOLE_ORDER_RETURN_STATUSES.includes(activeOrder?.status) || activeOrder?.paymentStatus === 'REFUND_PENDING');

  const showCodSection = isAdmin && Number(balances?.unsettledCod || 0) > 0;
  const showOverpaySection = isAdmin && Number(balances?.remainingOverpayment || 0) > 0;
  const showReallocateSection =
    activeOrder &&
    getOrderType(activeOrder) === 'PRODUCT' &&
    ['PENDING', 'CONFIRMED', 'PROCESSING'].includes(activeOrder.status);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Hoàn hàng & hoàn tiền: ${activeOrder?.orderNumber || activeOrder?.orderCode || `#${activeOrder?.id || ''}`}`}
      maxWidth="880px"
      footer={
        <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', gap: '8px', flexWrap: 'wrap' }}>
          <Button variant="outline" size="sm" icon={RefreshCw} onClick={loadOrderCommerce} loading={loading}>
            Làm mới dữ liệu
          </Button>
          <Button variant="primary" size="sm" onClick={onClose}>
            Đóng
          </Button>
        </div>
      }
    >
      {loading && !detail && (
        <p role="status" style={{ textAlign: 'center', padding: '24px 0', color: 'var(--text-muted)' }}>
          Đang tải thông tin hoàn hàng / đối soát của đơn...
        </p>
      )}

      {loadError && (
        <AlertBanner
          type="danger"
          message={loadError}
          action={<Button size="sm" onClick={loadOrderCommerce}>Thử lại</Button>}
        />
      )}

      {feedback && (
        <div style={{ marginBottom: '12px' }}>
          <AlertBanner type={feedback.type} message={feedback.message} />
        </div>
      )}

      {activeOrder && !loadError && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {/* Tóm tắt trạng thái đơn và số dư */}
          <div style={{ background: '#F8FAFC', border: '1px solid var(--border-subtle)', borderRadius: '10px', padding: '12px 14px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '8px', flexWrap: 'wrap', marginBottom: '10px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                <Badge variant={orderStatusBadge.variant}>{orderStatusBadge.text}</Badge>
                <Badge variant={payBadge.variant}>{payBadge.text}</Badge>
                <span style={{ fontSize: '12.5px', color: 'var(--text-muted)' }}>
                  {getPaymentMethodLabel(activeOrder.paymentMethod)}
                </span>
              </div>
              <div style={{ fontSize: '12.5px', color: '#475569' }}>
                Khách: <strong>{activeOrder.customerName}</strong> ({activeOrder.customerPhone})
              </div>
            </div>

            {balances && (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '8px' }}>
                <div style={{ background: '#FFFFFF', borderRadius: '8px', padding: '8px 10px', border: '1px solid #E2E8F0' }}>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Đã thanh toán</div>
                  <div style={{ fontWeight: 700, fontSize: '14px' }}>{formatCurrency(balances.paidAmount)}</div>
                </div>
                <div style={{ background: '#FFFFFF', borderRadius: '8px', padding: '8px 10px', border: '1px solid #E2E8F0' }}>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Đã hoàn cho khách</div>
                  <div style={{ fontWeight: 700, fontSize: '14px', color: '#059669' }}>{formatCurrency(balances.refundedAmount)}</div>
                </div>
                <div style={{ background: '#FFFFFF', borderRadius: '8px', padding: '8px 10px', border: '1px solid #E2E8F0' }}>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Nghĩa vụ hoàn chờ xử lý</div>
                  <div style={{ fontWeight: 700, fontSize: '14px', color: Number(balances.returnLiability) > 0 ? '#D97706' : '#0F172A' }}>
                    {formatCurrency(balances.returnLiability)}
                  </div>
                </div>
                {(Number(balances.unsettledCod) > 0 || activeOrder.paymentMethod === 'COD') && (
                  <div style={{ background: '#FFFFFF', borderRadius: '8px', padding: '8px 10px', border: '1px solid #E2E8F0' }}>
                    <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>COD chờ bàn giao</div>
                    <div style={{ fontWeight: 700, fontSize: '14px', color: Number(balances.unsettledCod) > 0 ? '#B45309' : '#0F172A' }}>
                      {formatCurrency(balances.unsettledCod)}
                    </div>
                  </div>
                )}
                {Number(balances.remainingOverpayment) > 0 && (
                  <div style={{ background: '#FFFBEB', borderRadius: '8px', padding: '8px 10px', border: '1px solid #FDE68A' }}>
                    <div style={{ fontSize: '11px', color: '#92400E' }}>Khách chuyển thừa</div>
                    <div style={{ fontWeight: 700, fontSize: '14px', color: '#B45309' }}>
                      {formatCurrency(balances.remainingOverpayment)}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* 1. Yêu cầu đổi trả từng sản phẩm của đơn hàng */}
          <section style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: '14px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
                <RotateCcw size={15} />
                <span>Yêu cầu hoàn hàng / hoàn tiền theo sản phẩm ({returns.length})</span>
              </h3>
            </div>
            {returns.length === 0 ? (
              <div style={{ fontSize: '13px', color: 'var(--text-muted)', padding: '12px', background: '#F8FAFC', borderRadius: '8px', border: '1px dashed var(--border-subtle)' }}>
                Đơn hàng này chưa có yêu cầu đổi trả từng sản phẩm từ khách hàng.
              </div>
            ) : (
              returns.map((ret) => (
                <ReturnRequestCard
                  key={ret.id}
                  ret={ret}
                  orderDetail={activeOrder}
                  itemMap={itemMap}
                  isAdmin={isAdmin}
                  onCompleted={handleMutated}
                />
              ))
            )}
          </section>

          {/* 2. Hoàn tiền toàn đơn (khi hủy / trả toàn bộ đơn và không vướng yêu cầu từng sản phẩm) */}
          {showWholeRefundCard && (
            <section style={{ border: '1px solid #FDE68A', background: '#FFFBEB', borderRadius: '10px', padding: '12px 14px' }}>
              <div style={{ fontWeight: 700, fontSize: '13.5px', color: '#92400E', marginBottom: '4px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <AlertTriangle size={15} />
                <span>Hoàn tiền toàn bộ đơn hàng</span>
              </div>
              {hasNonRejectedReturns ? (
                <p style={{ margin: 0, fontSize: '12.5px', color: '#78350F' }}>
                  Đơn hàng này đang có yêu cầu hoàn trả từng sản phẩm chưa bị từ chối. Vui lòng duyệt, nhận hàng và xác nhận hoàn tiền trực tiếp trên từng yêu cầu ở trên để đảm bảo hạch toán kho và sổ quỹ chính xác.
                </p>
              ) : canWholeOrderRefund ? (
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                  <p style={{ margin: 0, fontSize: '12.5px', color: '#78350F' }}>
                    Số tiền đã thu có thể hoàn lại toàn đơn: <strong>{formatCurrency(paidMinusRefunded)}</strong>.
                  </p>
                  <Button
                    size="sm"
                    variant="danger"
                    icon={DollarSign}
                    onClick={() => {
                      onClose?.();
                      onOpenWholeRefund?.(activeOrder);
                    }}
                  >
                    Xác nhận hoàn tiền toàn đơn
                  </Button>
                </div>
              ) : (
                <p style={{ margin: 0, fontSize: '12.5px', color: '#78350F' }}>
                  {!isAdmin
                    ? 'Chỉ tài khoản Quản trị viên (ADMIN) mới được xác nhận hoàn tiền toàn đơn.'
                    : 'Đơn hàng chưa có khoản thanh toán ròng cần hoàn lại toàn đơn.'}
                </p>
              )}
            </section>
          )}

          {/* 3. Nghiệp vụ đối soát COD / hoàn tiền chuyển thừa / đổi lô */}
          {(showCodSection || showOverpaySection || showReallocateSection) && (
            <section style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <h3 style={{ margin: 0, fontSize: '14px', fontWeight: 700 }}>
                Đối soát & điều chỉnh trực tiếp trên đơn
              </h3>

              {showCodSection && (
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    runOrderOp(
                      'cod',
                      () =>
                        apiClient.post(ENDPOINTS.ORDERS.COMMERCE_COD_SETTLEMENT(activeOrder.id), {
                          amount: Number(codAmount),
                          reference: codRef.trim(),
                          reason: codReason.trim(),
                        }),
                      'Đã ghi nhận bàn giao tiền COD từ tài xế/vận chuyển.'
                    );
                  }}
                  style={{ border: '1px solid var(--border-subtle)', borderRadius: '10px', padding: '12px 14px', display: 'flex', flexDirection: 'column', gap: '10px' }}
                >
                  <div style={{ fontWeight: 700, fontSize: '13px' }}>
                    Xác nhận bàn giao tiền COD (chờ thu: {formatCurrency(balances.unsettledCod)})
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '10px' }}>
                    <Input label="Số tiền COD thực nhận (VND)" type="number" min="1" step="1" value={codAmount} onChange={(e) => setCodAmount(e.target.value)} required />
                    <Input label="Mã chứng từ bàn giao" value={codRef} onChange={(e) => setCodRef(e.target.value)} maxLength={80} required />
                    <Input label="Nội dung xác nhận" value={codReason} onChange={(e) => setCodReason(e.target.value)} maxLength={250} required />
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <Button type="submit" size="sm" loading={opBusy === 'cod'}>
                      Ghi nhận bàn giao COD
                    </Button>
                  </div>
                </form>
              )}

              {showOverpaySection && (
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    runOrderOp(
                      'overpay',
                      () =>
                        apiClient.post(ENDPOINTS.ORDERS.COMMERCE_OVERPAYMENT_REFUND(activeOrder.id), {
                          amount: Number(overpayAmount),
                          reference: overpayRef.trim(),
                          reason: overpayReason.trim(),
                        }),
                      'Đã ghi nhận hoàn tiền chuyển thừa cho đơn hàng.'
                    );
                  }}
                  style={{ border: '1px solid var(--border-subtle)', borderRadius: '10px', padding: '12px 14px', display: 'flex', flexDirection: 'column', gap: '10px' }}
                >
                  <div style={{ fontWeight: 700, fontSize: '13px' }}>
                    Ghi nhận hoàn tiền chuyển thừa (số dư thừa: {formatCurrency(balances.remainingOverpayment)})
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '10px' }}>
                    <Input label="Số tiền đã hoàn thừa (VND)" type="number" min="1" step="1" value={overpayAmount} onChange={(e) => setOverpayAmount(e.target.value)} required />
                    <Input label="Mã chứng từ ngân hàng" value={overpayRef} onChange={(e) => setOverpayRef(e.target.value)} maxLength={80} required />
                    <Input label="Nội dung xác nhận" value={overpayReason} onChange={(e) => setOverpayReason(e.target.value)} maxLength={250} required />
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <Button type="submit" size="sm" loading={opBusy === 'overpay'}>
                      Xác nhận hoàn tiền thừa
                    </Button>
                  </div>
                </form>
              )}

              {showReallocateSection && (
                <div style={{ border: '1px solid var(--border-subtle)', borderRadius: '10px', padding: '12px 14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: '13px' }}>Đổi lô hàng khả dụng trước khi xuất</div>
                    <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                      Dùng khi lô đã giữ chỗ bị thu hồi hoặc cần thay thế sang lô đạt chuẩn còn hạn sử dụng.
                    </div>
                  </div>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    loading={opBusy === 'reallocate'}
                    onClick={() =>
                      runOrderOp(
                        'reallocate',
                        () => apiClient.post(ENDPOINTS.ORDERS.COMMERCE_REALLOCATE(activeOrder.id), {}),
                        'Đã tái phân bổ lô hàng khả dụng cho đơn.'
                      )
                    }
                  >
                    Thực hiện đổi lô
                  </Button>
                </div>
              )}
            </section>
          )}
        </div>
      )}
    </Modal>
  );
}
