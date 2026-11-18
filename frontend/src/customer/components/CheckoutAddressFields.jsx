import { useEffect, useState } from 'react';

import vietnamAddresses from '../data/vietnam-addresses.json';

const PROVINCES_API = 'https://www.tinhthanhpho.com/api/v1/new-provinces';

const fieldStyle = {
  width: '100%',
  minWidth: 0,
  padding: '9px 10px',
  fontSize: '12px',
  borderRadius: 'var(--radius-sm)',
  border: '1px solid var(--c-border)',
  backgroundColor: '#FFFCFA',
};

const labelStyle = {
  fontSize: '11px', fontWeight: 700, display: 'block', marginBottom: '4px',
};

function useLocations(url) {
  const [result, setResult] = useState(null);

  useEffect(() => {
    if (!url) return;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5000);
    let active = true;
    async function load() {
      try {
        const response = await fetch(url, { signal: controller.signal });
        if (!response.ok) throw new Error('Location lookup failed');
        const { data, metadata } = await response.json();
        if (!Array.isArray(data) || !data.length || data.length !== metadata?.total) {
          throw new Error('Incomplete location list');
        }
        if (active) setResult({ url, data });
      } catch {
        if (active) setResult({ url, error: true });
      } finally {
        clearTimeout(timer);
      }
    }
    load();
    return () => {
      active = false;
      clearTimeout(timer);
      controller.abort();
    };
  }, [url]);

  return result?.url === url ? result : null;
}

function LocationField({ id, label, value, onChange, options, placeholder, manual, disabled = false, required = false }) {
  return (
    <div>
      <label htmlFor={id} style={labelStyle}>{label}</label>
      {manual ? (
        <input id={id} style={fieldStyle} value={value} onChange={onChange}
          maxLength={100} required={required} />
      ) : (
        <select id={id} style={fieldStyle} value={value} onChange={onChange} required={required} disabled={disabled}>
          <option value="">{placeholder}</option>
          {value && !options.some((option) => option.name === value) && (
            <option value={value}>{value}</option>
          )}
          {options.map((option) => <option key={option.code} value={option.name}>{option.name}</option>)}
        </select>
      )}
    </div>
  );
}

export default function CheckoutAddressFields({ city, district, ward, onChange }) {
  const [manual, setManual] = useState(false);
  const provinces = useLocations(manual ? null : `${PROVINCES_API}?limit=100`);
  const provinceOptions = provinces?.data ?? vietnamAddresses.provinces;
  const selectedProvince = provinceOptions.find((province) => province.name === city);
  const wards = useLocations(!manual && selectedProvince
    ? `${PROVINCES_API}/${encodeURIComponent(selectedProvince.code)}/wards?limit=250`
    : null);
  const localWards = vietnamAddresses.wards[selectedProvince?.code];
  const wardOptions = (wards?.data ?? localWards ?? []).map(({ code, type, name }) => ({
    code, name: `${type} ${name}`,
  }));

  return (
    <div style={{ marginBottom: '14px' }}>
      <div className="customer-address-row"
        style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '10px' }}>
        <LocationField id="checkout-city" label="TỈNH / THÀNH *" value={city} required
          options={provinceOptions} manual={manual}
          placeholder="Chọn tỉnh / thành"
          onChange={(event) => onChange(manual ? { city: event.target.value } : { city: event.target.value, ward: '', district: '' })} />
        <LocationField id="checkout-ward" label="PHƯỜNG / XÃ / ĐẶC KHU *" value={ward} required
          options={wardOptions} manual={manual} disabled={!ward && !wardOptions.length}
          placeholder={selectedProvince && !wards && !localWards ? 'Đang tải phường / xã…' : 'Chọn phường / xã / đặc khu'}
          onChange={(event) => onChange(manual ? { ward: event.target.value } : { ward: event.target.value, district: '' })} />
        {(manual || district) && (
          <LocationField id="checkout-district" label="QUẬN / HUYỆN (ĐỊA CHỈ CŨ)" value={district}
            manual onChange={(event) => onChange({ district: event.target.value })} />
        )}
      </div>
      {wards?.error && !localWards && (
        <p role="status" style={{ fontSize: '12px' }}>Không tải được danh mục. Bạn có thể nhập địa chỉ trực tiếp.</p>
      )}
      <button type="button" onClick={() => setManual((current) => !current)}
        style={{ marginTop: '8px', color: 'var(--c-primary)', textDecoration: 'underline', fontSize: '12px' }}>
        {manual ? 'Chọn từ danh mục' : 'Nhập địa chỉ thủ công'}
      </button>
      <a href="https://www.tinhthanhpho.com/api-docs" target="_blank" rel="noreferrer"
        style={{ marginInlineStart: '12px', fontSize: '12px', textDecoration: 'underline' }}>
        Dữ liệu tỉnh thành
      </a>
    </div>
  );
}
