import React, { useState, useEffect } from 'react';
import { ChevronLeft, ChevronRight, Inbox, Loader2 } from 'lucide-react';
import { Button } from './Button';

// ponytail: smart sliding window pagination; upgrade to virtual list if rows > 100k
const getPaginationItems = (current, total) => {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }
  if (current <= 4) {
    return [1, 2, 3, 4, 5, '...', total];
  }
  if (current >= total - 3) {
    return [1, '...', total - 4, total - 3, total - 2, total - 1, total];
  }
  return [1, '...', current - 1, current, current + 1, '...', total];
};

export const DataTable = ({
  columns,
  data = [],
  loading = false,
  emptyMessage = 'Không có bản ghi dữ liệu nào.',
  page = 0,
  totalPages = 1,
  totalElements = 0,
  pageSize = 15,
  onPageChange,
}) => {
  const [jumpPage, setJumpPage] = useState(String(page + 1));

  useEffect(() => {
    setJumpPage(String(page + 1));
  }, [page]);

  const handleJump = (e) => {
    e?.preventDefault();
    const target = parseInt(jumpPage, 10);
    if (!Number.isFinite(target) || !onPageChange) return;
    const clamped = Math.max(1, Math.min(target, totalPages)) - 1;
    if (clamped !== page) {
      onPageChange(clamped);
    } else {
      setJumpPage(String(page + 1));
    }
  };
  return (
    <div className="data-table-container" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div className="table-wrapper" aria-busy={loading}>
        <table className="data-table">
          <thead>
            <tr>
              {columns.map((col, idx) => (
                <th key={idx} scope="col" style={{ width: col.width, textAlign: col.align || 'left' }}>
                  {col.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: 5 }, (_, rowIndex) => (
                <tr className="table-skeleton-row" key={`loading-${rowIndex}`} aria-hidden="true">
                  {columns.map((col, colIndex) => (
                    <td key={colIndex} style={{ textAlign: col.align || 'left' }}>
                      <span className="table-skeleton" style={{ width: `${[72, 54, 83, 60][(rowIndex + colIndex) % 4]}%`, animationDelay: `${rowIndex * 75}ms`, marginLeft: col.align === 'right' ? 'auto' : undefined }} />
                    </td>
                  ))}
                </tr>
              ))
            ) : data.length === 0 ? (
              <tr>
                <td colSpan={columns.length} className="table-empty-cell" style={{ textAlign: 'center', padding: '44px 20px' }}>
                  <div className="table-empty-state" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '10px', color: 'var(--text-muted)' }}>
                    <span className="table-empty-icon"><Inbox size={28} strokeWidth={1.4} aria-hidden="true" /></span>
                    <strong className="table-empty-title">Chưa có dữ liệu</strong>
                    <span>{emptyMessage}</span>
                  </div>
                </td>
              </tr>
            ) : data.map((row, rowIdx) => (
              <tr key={row.id ?? rowIdx}>
                {columns.map((col, colIdx) => (
                  <td key={colIdx} style={{ textAlign: col.align || 'left' }}>
                    {col.render
                      ? col.render(row)
                      : typeof col.accessor === 'function'
                        ? col.accessor(row)
                        : row[col.accessor] ?? '-'}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {loading && (
        <div className="table-loading-status" role="status" style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--text-muted)', fontSize: '12px' }}>
          <Loader2 size={14} className="animate-spin" aria-hidden="true" />
          Đang nạp dữ liệu…
        </div>
      )}

      {totalPages > 1 && (
        <nav className="table-pagination" aria-label="Phân trang dữ liệu" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', fontSize: '12px', color: 'var(--text-muted)', flexWrap: 'wrap' }}>
          <span className="table-pagination-summary">
            {totalElements > 0 ? (
              <>
                Hiển thị <strong style={{ color: 'var(--text-main)' }}>{page * pageSize + 1}–{Math.min((page + 1) * pageSize, totalElements)}</strong> của <strong style={{ color: 'var(--text-main)' }}>{totalElements}</strong> bản ghi (Trang <strong style={{ color: 'var(--text-main)' }}>{page + 1}</strong> / {totalPages})
              </>
            ) : (
              <>
                Trang <strong style={{ color: 'var(--text-main)' }}>{page + 1}</strong> / {totalPages}
              </>
            )}
          </span>
          <div className="table-pagination-controls" style={{ display: 'flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
            <Button variant="outline" size="sm" disabled={page <= 0 || loading || !onPageChange} onClick={() => onPageChange?.(page - 1)} icon={ChevronLeft} className="table-pagination-prev">
              Trang trước
            </Button>
            <div className="table-pagination-pages" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
              {getPaginationItems(page + 1, totalPages).map((item, idx) => {
                if (item === '...') {
                  return (
                    <span key={`ellipsis-${idx}`} className="table-pagination-ellipsis" aria-hidden="true" style={{ padding: '0 4px', color: 'var(--text-muted)', userSelect: 'none' }}>
                      …
                    </span>
                  );
                }
                const isCurrent = item === page + 1;
                return (
                  <Button
                    key={item}
                    variant={isCurrent ? 'primary' : 'outline'}
                    size="sm"
                    disabled={loading || !onPageChange}
                    onClick={() => onPageChange?.(item - 1)}
                    aria-current={isCurrent ? 'page' : undefined}
                    aria-label={`Trang ${item}`}
                    className={`table-pagination-page-btn ${isCurrent ? 'active' : ''}`}
                    style={{ minWidth: '34px', padding: '0 8px', justifyContent: 'center' }}
                  >
                    {item}
                  </Button>
                );
              })}
            </div>
            <Button variant="outline" size="sm" disabled={page >= totalPages - 1 || loading || !onPageChange} onClick={() => onPageChange?.(page + 1)} className="table-pagination-next">
              Trang tiếp <ChevronRight size={14} aria-hidden="true" />
            </Button>
          </div>
          <form onSubmit={handleJump} className="table-pagination-jump" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <label htmlFor="table-page-jump-input" style={{ whiteSpace: 'nowrap' }}>
              Đến trang:
            </label>
            <input
              id="table-page-jump-input"
              type="number"
              min={1}
              max={totalPages}
              value={jumpPage}
              disabled={loading || !onPageChange}
              onChange={(e) => setJumpPage(e.target.value)}
              onBlur={() => {
                if (!jumpPage || isNaN(parseInt(jumpPage, 10))) setJumpPage(String(page + 1));
              }}
              aria-label="Nhập số trang cần chuyển đến"
              className="table-pagination-jump-input"
              style={{
                width: '48px',
                height: '34px',
                textAlign: 'center',
                border: '1px solid var(--border-subtle, #e2e8f0)',
                background: 'var(--bg-surface, #fff)',
                color: 'var(--text-main, #0f172a)',
                fontSize: '12px',
                padding: '0 4px',
              }}
            />
            <Button
              type="submit"
              variant="outline"
              size="sm"
              disabled={loading || !onPageChange || !jumpPage}
              className="table-pagination-jump-btn"
              style={{ minHeight: '34px', height: '34px', padding: '0 12px', fontSize: '11px' }}
            >
              Đi
            </Button>
          </form>
        </nav>
      )}
    </div>
  );
};
