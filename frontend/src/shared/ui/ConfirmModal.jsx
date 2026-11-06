import React from 'react';
import { AlertTriangle, Trash2, AlertOctagon } from 'lucide-react';
import { Modal } from './Modal';
import { Button } from './Button';
import { Badge } from './Badge';

/**
 * Modal xác nhận thao tác quản trị:
 * - Xóa mềm (mặc định): Báo rõ dữ liệu được ẩn, có thể khôi phục.
 * - Xóa vĩnh viễn (isPermanent): Cảnh báo cấp cao màu đỏ, không thể hoàn tác.
 */
export const ConfirmModal = ({
  isOpen,
  onClose,
  onConfirm,
  title = 'Xác nhận xóa',
  message,
  detail,
  itemName,
  isPermanent = false,
  confirmText,
  cancelText = 'Hủy bỏ',
  loading = false,
}) => {
  const defaultConfirmText = isPermanent ? 'Xóa vĩnh viễn' : 'Xác nhận xóa';

  return (
    <Modal
      isOpen={isOpen}
      onClose={loading ? undefined : onClose}
      title={title}
      maxWidth="460px"
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', width: '100%' }}>
          <Button variant="outline" onClick={onClose} disabled={loading}>
            {cancelText}
          </Button>
          <Button
            variant="danger"
            loading={loading}
            onClick={onConfirm}
            icon={isPermanent ? AlertOctagon : Trash2}
          >
            {confirmText || defaultConfirmText}
          </Button>
        </div>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', padding: '4px 0' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
          <div
            style={{
              width: '42px',
              height: '42px',
              borderRadius: '8px',
              backgroundColor: isPermanent ? '#FEE2E2' : '#FEF3C7',
              color: isPermanent ? '#DC2626' : '#D97706',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            {isPermanent ? <AlertOctagon size={24} /> : <AlertTriangle size={24} />}
          </div>

          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
              <Badge variant={isPermanent ? 'danger' : 'warning'}>
                {isPermanent ? 'XÓA VĨNH VIỄN' : 'XÓA MỀM (ẨN DỮ LIỆU)'}
              </Badge>
            </div>

            <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-main, #1F2937)', marginTop: '2px' }}>
              {message || (itemName ? `Bạn có chắc muốn xóa "${itemName}"?` : 'Bạn có chắc chắn muốn thực hiện thao tác này?')}
            </div>

            <p style={{ fontSize: '13px', color: 'var(--text-muted, #4B5563)', margin: '8px 0 0', lineHeight: 1.5 }}>
              {detail || (
                isPermanent
                  ? 'CẢNH BÁO: Thao tác này sẽ xóa vĩnh viễn dữ liệu khỏi hệ thống, gỡ bỏ toàn bộ liên kết liên quan và KHÔNG THỂ HOÀN TÁC!'
                  : 'Dữ liệu sẽ được ẩn khỏi hệ thống quản trị và trải nghiệm người dùng, nhưng vẫn được lưu trữ bảo toàn trong cơ sở dữ liệu để phục vụ kiểm toán và có thể khôi phục.'
              )}
            </p>
          </div>
        </div>
      </div>
    </Modal>
  );
};
