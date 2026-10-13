import React, { useEffect, useState } from 'react';
import { Activity, RefreshCw, Save, Search, Sparkles } from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { Button } from '../shared/ui/Button';

const BASE = '/api/v1/admin/recommendations';
const FIELDS = [
  ['weight.view', 'Trọng số xem sản phẩm', 0, 20, 0.1],
  ['weight.search_click', 'Trọng số chọn kết quả tìm kiếm', 0, 20, 0.1],
  ['weight.add_to_cart', 'Trọng số thêm giỏ thành công', 0, 20, 0.1],
  ['weight.purchase', 'Trọng số mua thành công', 0, 20, 0.1],
  ['history_window', 'Số tương tác gần nhất', 1, 1000, 1],
  ['half_life_days', 'Thời gian giảm một nửa trọng số (ngày)', 0, 3650, 0.1],
  ['max_product_weight', 'Giới hạn đóng góp của một sản phẩm', 0.1, 100, 0.1],
  ['min_similarity', 'Ngưỡng điểm cosine', 0, 1, 0.01],
  ['catalog_refresh_seconds', 'Chu kỳ làm mới danh mục (giây)', 5, 3600, 1],
  ['field.name', 'Độ quan trọng của tên sản phẩm', 0, 10, 0.1],
  ['field.category', 'Độ quan trọng của danh mục', 0, 10, 0.1],
  ['field.brand', 'Độ quan trọng của thương hiệu', 0, 10, 0.1],
  ['field.description', 'Độ quan trọng của mô tả', 0, 10, 0.1],
  ['field.ingredients', 'Độ quan trọng của thành phần', 0, 10, 0.1],
  ['field.skin', 'Độ quan trọng của loại da', 0, 10, 0.1],
  ['field.concerns', 'Độ quan trọng của nhu cầu da', 0, 10, 0.1],
  ['field.formula', 'Độ quan trọng của thông tin công thức', 0, 10, 0.1],
];
const SOURCES = { CONTENT_BASED: 'Theo độ tương đồng nội dung', COLD_START: 'Sản phẩm phổ biến', MIXED: 'Nội dung + bổ sung sản phẩm phổ biến', UNAVAILABLE: 'Chưa có dữ liệu xác minh' };
const unwrap = response => response?.data ?? response;
const message = (error, fallback) => /[àáạâăèéêìíòóôơùúưỳýđ]/i.test(error?.message || '') ? error.message : fallback;
const grid = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 240px), 1fr))', gap: 16 };
const field = { display: 'grid', gap: 8, fontSize: 13, minWidth: 0 };

export const RecommendationAdminPanel = () => {
  const [status, setStatus] = useState(null);
  const [config, setConfig] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState(null);
  const [kind, setKind] = useState('product');
  const [productId, setProductId] = useState('1');
  const [userId, setUserId] = useState('');
  const [sessionId, setSessionId] = useState('');
  const [preview, setPreview] = useState(null);
  const [previewing, setPreviewing] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.allSettled([
      apiClient.get(`${BASE}/status`, { signal: controller.signal }),
      apiClient.get(`${BASE}/config`, { signal: controller.signal }),
    ]).then(([health, settings]) => {
      if (controller.signal.aborted) return;
      if (health.status === 'fulfilled') setStatus(unwrap(health.value));
      if (settings.status === 'fulfilled') setConfig(unwrap(settings.value));
      if (health.status === 'rejected' || settings.status === 'rejected') setFeedback({ error: true, text: 'Chưa tải đủ dữ liệu quản trị gợi ý. Vui lòng làm mới trạng thái.' });
      setLoading(false);
    });
    return () => controller.abort();
  }, []);

  const refresh = async () => {
    if (busy) return;
    setBusy(true); setFeedback(null);
    try {
      const data = unwrap(await apiClient.post(`${BASE}/refresh`));
      setStatus(data);
      if (!config) setConfig(unwrap(await apiClient.get(`${BASE}/config`)));
      setFeedback({ error: data.status !== 'ok', text: data.status === 'ok' ? 'Đã làm mới dữ liệu gợi ý từ danh mục hiện tại.' : data.error || data.config_error || 'Chưa làm mới được toàn bộ dữ liệu.' });
    } catch (error) { setFeedback({ error: true, text: message(error, 'Chưa làm mới được hệ thống gợi ý.') }); }
    finally { setBusy(false); }
  };

  const save = async event => {
    event.preventDefault();
    if (busy || !config) return;
    const payload = { ...config };
    for (const [key, , min, max, step] of FIELDS) {
      const value = Number(config[key]);
      if (config[key] === '' || !Number.isFinite(value) || value < min || value > max || (step === 1 && !Number.isInteger(value))) {
        setFeedback({ error: true, text: 'Vui lòng kiểm tra phạm vi và kiểu số của các cấu hình.' }); return;
      }
      payload[key] = value;
    }
    if (!FIELDS.filter(([key]) => key.startsWith('weight.')).some(([key]) => payload[key] > 0)) {
      setFeedback({ error: true, text: 'Cần ít nhất một trọng số hành vi lớn hơn 0.' }); return;
    }
    if (!FIELDS.filter(([key]) => key.startsWith('field.')).some(([key]) => payload[key] > 0)) {
      setFeedback({ error: true, text: 'Cần ít nhất một nhóm thuộc tính có trọng số lớn hơn 0.' }); return;
    }
    setBusy(true); setFeedback(null);
    try {
      const response = await apiClient.put(`${BASE}/config`, payload);
      const data = unwrap(response);
      setConfig(data.config);
      setFeedback({ error: !data.applied, text: response.message || (data.applied ? 'Đã áp dụng cấu hình gợi ý.' : 'Đã lưu cấu hình; đang chờ dịch vụ gợi ý đọc lại.') });
      try { setStatus(unwrap(await apiClient.get(`${BASE}/status`))); } catch { /* Saved values remain visible. */ }
    } catch (error) { setFeedback({ error: true, text: message(error, 'Chưa lưu được cấu hình. Dữ liệu đã nhập vẫn được giữ.') }); }
    finally { setBusy(false); }
  };

  const inspect = async event => {
    event.preventDefault();
    if (previewing) return;
    const params = new URLSearchParams({ limit: '8' });
    if (kind === 'product') {
      if (!Number.isSafeInteger(Number(productId)) || Number(productId) <= 0) { setFeedback({ error: true, text: 'ID sản phẩm phải là số nguyên dương.' }); return; }
      params.set('productId', productId);
    } else {
      if (userId) {
        if (!Number.isSafeInteger(Number(userId)) || Number(userId) <= 0) { setFeedback({ error: true, text: 'ID khách hàng phải là số nguyên dương.' }); return; }
        params.set('userId', userId);
      }
      if (sessionId.trim()) params.set('sessionId', sessionId.trim());
    }
    setPreviewing(true); setFeedback(null);
    try { setPreview(unwrap(await apiClient.get(`${BASE}/preview?${params}`))); }
    catch (error) { setFeedback({ error: true, text: message(error, 'Chưa lấy được kết quả xem thử.') }); }
    finally { setPreviewing(false); }
  };

  return <div style={{ display: 'grid', gap: 20 }}>
    <section className="card" aria-label="Trạng thái Content-Based">
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div><h2 style={{ fontSize: 18 }}><Activity size={18} /> Gợi ý sản phẩm Content-Based</h2><p>TF-IDF theo thuộc tính · Hồ sơ hành vi có trọng số · Xếp hạng cosine.</p></div>
        <Button variant="outline" icon={RefreshCw} loading={busy} onClick={refresh}>Làm mới dữ liệu gợi ý</Button>
      </div>
      {loading ? <p role="status">Đang tải trạng thái gợi ý…</p> : status ? <div style={grid}>
        <div><strong>{status.status === 'ok' ? 'Đang hoạt động' : 'Cần kiểm tra kết nối/cấu hình'}</strong><p>{status.indexed_products} sản phẩm · {status.features} đặc trưng</p></div>
        <div><strong>Danh mục tự cập nhật</strong><p>Mỗi {status.config?.catalog_refresh_seconds ?? '—'} giây. Cấu hình được đọc lại trong tối đa 5 giây.</p></div>
        <div><strong>Phiên bản nội dung: {status.index_version || 'Chưa xác minh'}</strong><p>Lần cập nhật: {status.last_refresh ? new Date(status.last_refresh).toLocaleString('vi-VN') : 'Chưa xác minh'}</p></div>
      </div> : <p role="alert">Chưa xác minh trạng thái hệ thống gợi ý.</p>}
      {status?.error && <p role="alert">{status.error}</p>}{status?.config_error && <p role="alert">{status.config_error}</p>}
      {feedback && <div role={feedback.error ? 'alert' : 'status'} style={{ marginTop: 12, color: feedback.error ? '#A13250' : '#16724D' }}>{feedback.text}</div>}
    </section>
    <section className="card"><h2 style={{ fontSize: 18 }}>Điều chỉnh thuật toán</h2>
      <p>Trọng số điều khiển đóng góp hành vi vào hồ sơ sở thích. Điểm cosine là độ tương đồng nội dung, không phải tỷ lệ hiệu quả sản phẩm.</p>
      {config ? <form onSubmit={save}><fieldset disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
        <div style={grid}>{FIELDS.map(([key, label, min, max, step]) => <label key={key} style={field}>{label}<input className="form-input" type="number" min={min} max={max} step={step} required value={config[key] ?? ''} onChange={event => setConfig(previous => ({ ...previous, [key]: event.target.value }))} /><small>{min}–{max}{key === 'half_life_days' ? ' · 0 để không giảm theo thời gian' : ''}</small></label>)}</div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 20, marginBlock: 18 }}>{[['exclude_seen', 'Loại sản phẩm đã tương tác'], ['in_stock_only', 'Chỉ gợi ý sản phẩm còn tồn khả dụng']].map(([key, label]) => <label key={key}><input type="checkbox" checked={Boolean(config[key])} onChange={event => setConfig(previous => ({ ...previous, [key]: event.target.checked }))} /> {label}</label>)}</div>
        <Button type="submit" icon={Save} loading={busy}>Lưu và áp dụng cấu hình</Button>
      </fieldset></form> : <p>Chưa tải được cấu hình. Bạn có thể làm mới để thử lại.</p>}
    </section>
    <section className="card"><h2 style={{ fontSize: 18 }}><Sparkles size={18} /> Xem thử và giải thích kết quả</h2>
      <form onSubmit={inspect} style={{ display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'end' }}>
        <label style={field}>Kiểu gợi ý<select className="form-select" value={kind} onChange={event => { setKind(event.target.value); setPreview(null); }}><option value="product">Sản phẩm tương tự</option><option value="user">Theo lịch sử khách hàng / phiên</option></select></label>
        {kind === 'product' ? <label style={field}>ID sản phẩm<input className="form-input" type="number" min={1} step={1} required value={productId} onChange={event => setProductId(event.target.value)} /></label> : <><label style={field}>ID khách hàng (không bắt buộc)<input className="form-input" type="number" min={1} step={1} value={userId} onChange={event => setUserId(event.target.value)} /></label><label style={field}>Mã phiên (không bắt buộc)<input className="form-input" maxLength={100} value={sessionId} onChange={event => setSessionId(event.target.value)} /></label></>}
        <Button type="submit" icon={Search} loading={previewing}>Xem gợi ý</Button>
      </form>
      {preview && <div aria-live="polite" style={{ marginTop: 20 }}><p>Nguồn: <strong>{SOURCES[preview.source] || preview.source}</strong> · {preview.total} kết quả · {preview.matched_history_products || 0} sản phẩm có tín hiệu lịch sử</p>
        {preview.history_error && <p role="alert">{preview.history_error}</p>}
        {preview.items?.length ? <ol style={{ display: 'grid', gap: 12, paddingLeft: 22 }}>{preview.items.map(item => <li key={item.id}><strong>#{item.id} · {item.name}</strong><p style={{ marginBlock: 4 }}>Cosine: {item.score == null ? 'Không áp dụng cho fallback phổ biến' : Number(item.score).toFixed(4)} · {item.reason}</p></li>)}</ol> : <p>Chưa có ứng viên phù hợp với dữ liệu và điều kiện hiện tại.</p>}
      </div>}
    </section>
  </div>;
};
