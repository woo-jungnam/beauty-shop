import React from 'react';
import { ChevronLeft, ChevronRight, Inbox, Loader2 } from 'lucide-react';
import { Button } from './Button';

export const DataTable = ({
  columns,
  data = [],
  loading = false,
  emptyMessage = 'Không có bản ghi dữ liệu nào.',
  page = 0,
  totalPages = 1,
  totalElements = 0,
  onPageChange,
}) => {
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
        <nav className="table-pagination" aria-label="Phân trang dữ liệu" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', fontSize: '12px', color: 'var(--text-muted)' }}>
          <span className="table-pagination-summary">
            Trang <strong style={{ color: 'var(--text-main)' }}>{page + 1}</strong> / {totalPages}
            <span className="table-pagination-divider" aria-hidden="true"> · </span>
            <strong style={{ color: 'var(--text-main)' }}>{totalElements}</strong> bản ghi
          </span>
          <div className="table-pagination-controls" style={{ display: 'flex', gap: '6px' }}>
            <Button variant="outline" size="sm" disabled={page <= 0 || loading || !onPageChange} onClick={() => onPageChange?.(page - 1)} icon={ChevronLeft}>
              Trang trước
            </Button>
            <Button variant="outline" size="sm" disabled={page >= totalPages - 1 || loading || !onPageChange} onClick={() => onPageChange?.(page + 1)}>
              Trang tiếp <ChevronRight size={14} aria-hidden="true" />
            </Button>
          </div>
        </nav>
      )}
    </div>
  );
};
