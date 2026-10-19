import React, { useEffect, useRef, useState } from 'react';
import { Camera, Search, RefreshCw } from 'lucide-react';
import { ProductCard } from '../../customer/components/product/ProductCard';
import { searchProductsByImage } from './imageSearchApi';
import './image-search.css';

const MAX_BYTES = 5 * 1024 * 1024;
const TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

export function ImageSearch({ onNavigate }) {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const pending = useRef(null);
  const version = useRef(0);
  const input = useRef(null);
  const previewUrl = useRef('');

  useEffect(() => () => {
    version.current += 1;
    pending.current?.abort();
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
  }, []);

  function choose(selected) {
    version.current += 1;
    pending.current?.abort();
    pending.current = null;
    setLoading(false);
    setResult(null);
    setError('');
    setFile(null);
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = '';
    setPreview('');
    if (!selected) return;
    if (!TYPES.has(selected.type)) {
      setError('Vui lòng chọn ảnh JPEG, PNG hoặc WebP.');
    } else if (selected.size === 0 || selected.size > MAX_BYTES) {
      setError('Ảnh cần có dữ liệu và không vượt quá 5 MiB.');
    } else {
      setFile(selected);
      previewUrl.current = URL.createObjectURL(selected);
      setPreview(previewUrl.current);
    }
  }

  async function search(event) {
    event.preventDefault();
    if (!file || pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    const current = ++version.current;
    setLoading(true);
    setError('');
    setResult(null);
    try {
      const data = await searchProductsByImage(file, controller.signal);
      if (current === version.current) setResult(data);
    } catch (err) {
      if (current === version.current && !controller.signal.aborted)
        setError(err.message || 'Không tìm được ảnh. Vui lòng thử lại.');
    } finally {
      if (current === version.current) {
        pending.current = null;
        setLoading(false);
      }
    }
  }

  return (
    <div className="customer-container image-search-page">
      <a href="#/products">← Danh mục sản phẩm</a>
      <h1><Camera aria-hidden="true" /> Tìm sản phẩm bằng ảnh</h1>
      <p>Chụp rõ một sản phẩm hoặc bao bì để tìm trong cửa hàng. Hỗ trợ JPEG, PNG, WebP, tối đa 5 MiB.</p>
      <form className="image-search-panel" onSubmit={search} aria-busy={loading}>
        <label htmlFor="product-search-image">Chọn hoặc chụp ảnh sản phẩm</label>
        <input ref={input} id="product-search-image" type="file" accept="image/jpeg,image/png,image/webp"
          onChange={event => choose(event.target.files?.[0])} />
        {preview && <img className="image-search-preview" src={preview} alt="Ảnh sản phẩm bạn đã chọn" />}
        {file && <span>{file.name}</span>}
        <div className="image-search-buttons">
          <button type="submit" disabled={!file || loading}><Search size={17} aria-hidden="true" />{loading ? 'Đang tìm…' : 'Tìm bằng ảnh'}</button>
          <button type="button" onClick={() => { choose(null); if (input.current) input.current.value = ''; }}>
            <RefreshCw size={17} aria-hidden="true" /> Đổi ảnh
          </button>
        </div>
      </form>
      {error && <div className="image-search-error" role="alert">{error}</div>}
      {loading && <p role="status">Đang tìm sản phẩm có hình ảnh gần giống…</p>}
      {result && <section aria-label="Kết quả tìm kiếm bằng ảnh" aria-live="polite">
        <h2>Sản phẩm có hình ảnh gần giống</h2>
        <p>Vui lòng kiểm tra tên và dung tích trước khi chọn. Danh sách chỉ gồm sản phẩm còn hàng tại thời điểm tìm kiếm.</p>
        {result.items?.length ? <div className="image-search-results">
          {result.items.map(item => <div key={item.product.id}>
            <ProductCard product={item.product} onNavigate={onNavigate} />
            <p className="image-search-stock">{item.inStock ? 'Còn hàng' : 'Hết hàng'}</p>
            <button type="button" className="image-search-detail" onClick={() => onNavigate(item.detailPath)}>Xem chi tiết và chọn dung tích</button>
          </div>)}
        </div> : <p role="status">Chưa có sản phẩm phù hợp còn hàng trong kết quả. Bạn có thể chọn ảnh khác hoặc tìm theo tên.</p>}
      </section>}
    </div>
  );
}
