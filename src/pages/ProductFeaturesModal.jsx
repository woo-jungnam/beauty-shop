import React, { useEffect, useState } from 'react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { Modal } from '../shared/ui/Modal';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { attributePayload, ingredientPayload, ingredientCatalog } from './productFeatures.js';

const dataOf = (response) => response.data ?? response;

export const ProductFeaturesModal = ({ product, onClose }) => {
  const [detail, setDetail] = useState(null);
  const [definitions, setDefinitions] = useState([]);
  const [ingredients, setIngredients] = useState([]);
  const [rows, setRows] = useState([]);
  const [mappings, setMappings] = useState([]);
  const [saved, setSaved] = useState({ attributes: '', ingredients: '' });
  const [loading, setLoading] = useState(true);
  const [ready, setReady] = useState(false);
  const [saving, setSaving] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setReady(false);
      setError('');
      try {
        const [productResponse, definitionsResponse, mappingsResponse] = await Promise.all([
          apiClient.get(ENDPOINTS.CATALOG.PRODUCT_DETAIL(product.id)),
          apiClient.get(ENDPOINTS.ATTRIBUTES.LIST),
          apiClient.get(ENDPOINTS.CATALOG.PRODUCT_INGREDIENTS(product.id)),
        ]);
        const catalog = await ingredientCatalog((page) => apiClient.get(`${ENDPOINTS.CATALOG.INGREDIENTS}?page=${page}&size=100`));
        const productDetail = dataOf(productResponse);
        // An older backend must not turn an unsupported field into an empty replacement.
        if (!Array.isArray(productDetail.attributeValues)) throw new Error('Backend chưa hỗ trợ thuộc tính sản phẩm. Cần cập nhật backend trước khi lưu.');
        const attributeRows = productDetail.attributeValues.map((value) => ({
          attributeDefinitionId: String(value.attributeDefinitionId),
          productVariantId: value.productVariantId == null ? '' : String(value.productVariantId),
          value: value.value ?? '',
        }));
        const ingredientRows = dataOf(mappingsResponse).map((value) => ({
          ingredientId: String(value.ingredientId),
          ingredientName: value.ingredientName,
          concentration: value.concentration == null ? '' : String(value.concentration),
          concentrationUnit: value.concentrationUnit ?? '%',
          keyActive: Boolean(value.keyActive),
          displayOrder: value.displayOrder ?? 0,
        }));
        if (cancelled) return;
        setDetail(productDetail);
        setDefinitions(dataOf(definitionsResponse));
        setIngredients(catalog);
        setRows(attributeRows);
        setMappings(ingredientRows);
        setSaved({ attributes: JSON.stringify(attributeRows), ingredients: JSON.stringify(ingredientRows) });
        setReady(true);
      } catch (err) {
        if (!cancelled) setError(err.message || 'Không tải được dữ liệu. Chưa thể lưu.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, [product.id, attempt]);

  const close = () => {
    if (saving) return;
    const dirty = ready && (saved.attributes !== JSON.stringify(rows) || saved.ingredients !== JSON.stringify(mappings));
    if (!dirty || window.confirm('Có thay đổi chưa lưu. Đóng và bỏ các thay đổi này?')) onClose();
  };
  const changeRow = (index, patch) => setRows(rows.map((row, i) => i === index ? { ...row, ...patch } : row));
  const changeMapping = (index, patch) => setMappings(mappings.map((row, i) => i === index ? { ...row, ...patch } : row));

  const save = async (kind, event) => {
    event.preventDefault();
    if (!ready || saving) return;
    setError('');
    setMessage('');
    try {
      const payload = kind === 'attributes' ? attributePayload(rows, definitions) : ingredientPayload(mappings);
      if (!payload.length && !window.confirm(`Gỡ toàn bộ ${kind === 'attributes' ? 'thuộc tính' : 'thành phần'} khỏi sản phẩm?`)) return;
      setSaving(kind);
      await apiClient.put(kind === 'attributes'
        ? ENDPOINTS.CATALOG.PRODUCT_ATTRIBUTES(product.id)
        : ENDPOINTS.CATALOG.PRODUCT_INGREDIENTS(product.id), payload);
      setSaved((previous) => ({ ...previous, [kind]: JSON.stringify(kind === 'attributes' ? rows : mappings) }));
      setMessage(`Đã lưu ${kind === 'attributes' ? 'thuộc tính' : 'thành phần'}. Phần còn lại có nút lưu riêng.`);
    } catch (err) {
      setError(err.message || 'Lưu thất bại. Các thay đổi vẫn được giữ để thử lại.');
    } finally {
      setSaving('');
    }
  };

  return (
    <Modal isOpen onClose={close} title={`Thuộc tính & Thành phần — ${product.name}`} maxWidth="800px">
      <p>Sản phẩm #{product.id}. Hai phần bên dưới được lưu riêng. INCI nguyên văn được chỉnh trong form sản phẩm.</p>
      {loading && <p role="status">Đang tải dữ liệu…</p>}
      {error && <p role="alert" style={{ color: 'var(--color-danger-700)' }}>{error}</p>}
      {message && <p role="status">{message}</p>}
      {!loading && !ready && <Button onClick={() => setAttempt(attempt + 1)}>Tải lại</Button>}
      {ready && <>
        <form onSubmit={(event) => save('attributes', event)}>
          <fieldset disabled={Boolean(saving)} style={{ border: 0, padding: 0, minWidth: 0 }}>
            <h4>Thuộc tính đặc trưng</h4>
            <p>Định nghĩa mới được tạo tại tab “Thuộc tính mở rộng”. Chọn phạm vi toàn sản phẩm hoặc một SKU.</p>
            {rows.map((row, index) => {
              const definition = definitions.find((item) => String(item.id) === row.attributeDefinitionId);
              const label = `Giá trị thuộc tính ${index + 1}`;
              return <div key={index} style={{ padding: '12px 0', borderBottom: '1px solid var(--border-subtle)' }}>
                <div className="grid-2">
                  <Select label={`Thuộc tính ${index + 1}`} required value={row.attributeDefinitionId}
                    onChange={(event) => changeRow(index, { attributeDefinitionId: event.target.value, value: '' })}
                    options={[{ value: '', label: '-- Chọn thuộc tính --' }, ...definitions.map((item) => ({ value: String(item.id), label: `${item.name} (${item.dataType})` }))]} />
                  <Select label={`Phạm vi thuộc tính ${index + 1}`} value={row.productVariantId}
                    onChange={(event) => changeRow(index, { productVariantId: event.target.value })}
                    options={[{ value: '', label: 'Toàn sản phẩm' }, ...(detail.variants || []).map((variant) => ({ value: String(variant.id), label: `${variant.variantName} — ${variant.sku}` }))]} />
                </div>
                {definition?.dataType === 'BOOLEAN'
                  ? <Select label={label} required value={row.value} onChange={(event) => changeRow(index, { value: event.target.value })}
                    options={[{ value: '', label: '-- Chọn --' }, { value: 'true', label: 'Có' }, { value: 'false', label: 'Không' }]} />
                  : definition?.dataType === 'TEXT_AREA'
                    ? <label className="form-group">{label}<textarea className="form-textarea" required value={row.value} onChange={(event) => changeRow(index, { value: event.target.value })} /></label>
                    : <Input label={label} required type={definition?.dataType === 'NUMBER' ? 'number' : definition?.dataType === 'DATE' ? 'date' : 'text'}
                      step={definition?.dataType === 'NUMBER' ? 'any' : undefined} maxLength={definition?.dataType === 'STRING' ? 500 : undefined}
                      value={row.value} onChange={(event) => changeRow(index, { value: event.target.value })} />}
                <Button variant="outline" onClick={() => setRows(rows.filter((_, i) => i !== index))} aria-label={`Gỡ thuộc tính ${index + 1}`}>Gỡ thuộc tính</Button>
              </div>;
            })}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, margin: '12px 0' }}>
              <Button variant="outline" disabled={!definitions.length} onClick={() => setRows([...rows, { attributeDefinitionId: '', productVariantId: '', value: '' }])}>Thêm dòng thuộc tính</Button>
              <Button type="submit" loading={saving === 'attributes'}>Lưu thuộc tính</Button>
            </div>
          </fieldset>
        </form>
        <form onSubmit={(event) => save('ingredients', event)}>
          <fieldset disabled={Boolean(saving)} style={{ border: 0, padding: 0, minWidth: 0 }}>
            <h4>Thành phần & hoạt chất của sản phẩm</h4>
            <p>Chọn từ danh mục. Chỉ nhập nồng độ khi có thông tin xác thực; không suy ra từ thứ tự INCI.</p>
            {mappings.map((row, index) => <div key={index} style={{ padding: '12px 0', borderBottom: '1px solid var(--border-subtle)' }}>
              <Select label={`Thành phần ${index + 1}`} required value={row.ingredientId}
                onChange={(event) => changeMapping(index, { ingredientId: event.target.value })}
                options={[{ value: '', label: '-- Chọn thành phần --' }, ...(!ingredients.some((item) => String(item.id) === row.ingredientId) && row.ingredientId ? [{ value: row.ingredientId, label: `${row.ingredientName || row.ingredientId} (đã xóa — cần gỡ/thay)` }] : []), ...ingredients.map((item) => ({ value: String(item.id), label: `${item.name} — ${item.inciName}` }))]} />
              <div className="grid-2">
                <Input label={`Nồng độ ${index + 1} (tùy chọn)`} type="number" min="0" step="any" value={row.concentration} onChange={(event) => changeMapping(index, { concentration: event.target.value })} />
                <Input label={`Đơn vị ${index + 1}`} required maxLength={10} value={row.concentrationUnit} onChange={(event) => changeMapping(index, { concentrationUnit: event.target.value })} />
              </div>
              <Input label={`Thứ tự hiển thị ${index + 1}`} type="number" min="0" step="1" required value={row.displayOrder} onChange={(event) => changeMapping(index, { displayOrder: event.target.value })} />
              <label style={{ display: 'block', margin: '8px 0' }}><input type="checkbox" checked={row.keyActive} onChange={(event) => changeMapping(index, { keyActive: event.target.checked })} /> Hoạt chất nổi bật của sản phẩm</label>
              <Button variant="outline" onClick={() => setMappings(mappings.filter((_, i) => i !== index))} aria-label={`Gỡ thành phần ${index + 1}`}>Gỡ thành phần</Button>
            </div>)}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, margin: '12px 0' }}>
              <Button variant="outline" disabled={!ingredients.length} onClick={() => setMappings([...mappings, { ingredientId: '', concentration: '', concentrationUnit: '%', keyActive: false, displayOrder: mappings.length }])}>Thêm dòng thành phần</Button>
              <Button type="submit" loading={saving === 'ingredients'}>Lưu thành phần</Button>
            </div>
          </fieldset>
        </form>
      </>}
      <Button variant="outline" disabled={Boolean(saving)} onClick={close}>Đóng</Button>
    </Modal>
  );
};
