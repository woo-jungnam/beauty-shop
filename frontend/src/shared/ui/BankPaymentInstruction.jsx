import React, { useState } from 'react';
import { Button } from './Button';

export const BankPaymentInstruction = ({ instruction, label = 'Mã VietQR thanh toán Spa' }) => {
  const [failedUrl, setFailedUrl] = useState(null);
  const [message, setMessage] = useState('');
  const copy = async text => {
    try { await navigator.clipboard.writeText(text); setMessage('Đã sao chép.'); }
    catch { setMessage('Không thể sao chép. Vui lòng sao chép thông tin thủ công.'); }
  };
  if (!instruction) return null;
  return <div className="checkout-bank">
    {instruction.qrCodeUrl && failedUrl !== instruction.qrCodeUrl && <img src={instruction.qrCodeUrl} alt={label} onError={() => setFailedUrl(instruction.qrCodeUrl)} style={{ width: 280, maxWidth: '100%', height: 'auto' }} />}
    {failedUrl === instruction.qrCodeUrl && <p role="alert">Không tải được QR. Chuyển khoản theo thông tin dưới đây.</p>}
    <p>Ngân hàng: <strong>{instruction.bankName}</strong></p>
    <p>Chủ tài khoản: <strong>{instruction.bankAccountName}</strong></p>
    <p>Số tài khoản: <strong>{instruction.bankAccountNumber}</strong> <Button type="button" size="sm" variant="outline" onClick={() => copy(instruction.bankAccountNumber)}>Sao chép số tài khoản</Button></p>
    <p>Nội dung: <strong>{instruction.transferSyntax}</strong> <Button type="button" size="sm" variant="outline" onClick={() => copy(instruction.transferSyntax)}>Sao chép nội dung</Button></p>
    <p>Chuyển đúng nội dung. Chỉ hoàn tất khi hệ thống xác nhận nhận tiền.</p>
    {message && <p role="status">{message}</p>}
  </div>;
};
