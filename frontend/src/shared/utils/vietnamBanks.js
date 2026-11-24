export const VIETNAM_BANKS = [
  { bin: '970422', shortName: 'MBBank', name: 'Ngân hàng TMCP Quân Đội (MBBank)' },
  { bin: '970436', shortName: 'Vietcombank', name: 'Ngân hàng TMCP Ngoại Thương (Vietcombank)' },
  { bin: '970415', shortName: 'VietinBank', name: 'Ngân hàng TMCP Công Thương (VietinBank)' },
  { bin: '970418', shortName: 'BIDV', name: 'Ngân hàng TMCP Đầu Tư & Phát Triển (BIDV)' },
  { bin: '970407', shortName: 'Techcombank', name: 'Ngân hàng TMCP Kỹ Thương (Techcombank)' },
  { bin: '970416', shortName: 'ACB', name: 'Ngân hàng TMCP Á Châu (ACB)' },
  { bin: '970423', shortName: 'TPBank', name: 'Ngân hàng TMCP Tiên Phong (TPBank)' },
  { bin: '970432', shortName: 'VPBank', name: 'Ngân hàng TMCP Việt Nam Thịnh Vượng (VPBank)' },
  { bin: '970405', shortName: 'Agribank', name: 'Ngân hàng Nông Nghiệp & PTNT (Agribank)' },
  { bin: '970403', shortName: 'Sacombank', name: 'Ngân hàng TMCP Sài Gòn Thương Tín (Sacombank)' },
  { bin: '970441', shortName: 'VIB', name: 'Ngân hàng TMCP Quốc Tế (VIB)' },
  { bin: '970448', shortName: 'OCB', name: 'Ngân hàng TMCP Phương Đông (OCB)' },
  { bin: '970426', shortName: 'MSB', name: 'Ngân hàng TMCP Hàng Hải (MSB)' },
  { bin: '970437', shortName: 'HDBank', name: 'Ngân hàng TMCP Phát Triển TP.HCM (HDBank)' },
  { bin: '970443', shortName: 'SHB', name: 'Ngân hàng TMCP Sài Gòn - Hà Nội (SHB)' },
  { bin: '970440', shortName: 'SeABank', name: 'Ngân hàng TMCP Đông Nam Á (SeABank)' },
  { bin: '970431', shortName: 'Eximbank', name: 'Ngân hàng TMCP Xuất Nhập Khẩu (Eximbank)' },
];

export function buildVietQrUrl({ bankBin, accountNumber, accountName, amount, addInfo }) {
  const bin = String(bankBin || '').trim();
  const acc = String(accountNumber || '').trim();
  if (!bin || !acc) return '';
  const params = new URLSearchParams();
  const cleanAmount = Math.round(Number(amount || 0));
  if (cleanAmount > 0) params.set('amount', String(cleanAmount));
  if (addInfo) params.set('addInfo', String(addInfo).trim());
  if (accountName) params.set('accountName', String(accountName).trim());
  return `https://img.vietqr.io/image/${encodeURIComponent(bin)}-${encodeURIComponent(acc)}-compact.jpg?${params.toString()}`;
}

