import React, { useEffect } from 'react';
import { X, FlaskConical, CheckCircle2, AlertTriangle, ArrowRight } from 'lucide-react';

export const IngredientModal = ({ ingredient, isOpen, onClose, onNavigate }) => {
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen || !ingredient) return null;

  return (
    <div
      className="drawer-backdrop"
      onClick={onClose}
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px',
        zIndex: 1100
      }}
    >
      <div
        className="luxury-card"
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%',
          maxWidth: '680px',
          backgroundColor: '#FFFFFF',
          borderRadius: 'var(--radius-lg)',
          overflow: 'hidden',
          padding: '32px',
          position: 'relative',
          animation: 'fadeInUp 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
          boxShadow: '0 25px 60px -15px rgba(0, 0, 0, 0.4)'
        }}
      >
        {/* Close Button */}
        <button
          onClick={onClose}
          aria-label="Đóng bảng giải mã hoạt chất"
          style={{
            position: 'absolute',
            top: '18px',
            right: '18px',
            width: '32px',
            height: '32px',
            borderRadius: '50%',
            backgroundColor: 'var(--c-canvas)',
            border: '1px solid var(--c-border)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            color: 'var(--c-primary)'
          }}
        >
          <X size={15} />
        </button>

        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
          <span className="badge-dermatology safe" style={{ fontSize: '11px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <FlaskConical size={13} />
            <span>BẢN ĐỒ DƯỢC LÝ INCI</span>
          </span>
          <span style={{ fontSize: '11px', color: 'var(--c-gold-hover)', fontWeight: 700 }}>
            {ingredient.ewg}
          </span>
        </div>

        <h2 style={{
          fontSize: '24px',
          fontWeight: 700,
          margin: '0 0 10px 0',
          color: 'var(--c-primary)',
          fontFamily: 'var(--font-serif)'
        }}>
          {ingredient.name}
        </h2>

        <p style={{ fontSize: '14px', color: 'var(--c-text-muted)', lineHeight: 1.6, margin: '0 0 24px 0' }}>
          {ingredient.desc}
        </p>

        {/* Scientific Metrics Grid */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(3, 1fr)',
          gap: '14px',
          marginBottom: '24px'
        }}>
          <div style={{ backgroundColor: 'var(--c-canvas)', padding: '14px', borderRadius: '8px', border: '1px solid var(--c-border-subtle)' }}>
            <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Nồng độ khuyến nghị</div>
            <div style={{ fontSize: '15px', fontWeight: 800, color: 'var(--c-primary)', marginTop: '4px', fontFamily: 'var(--font-mono)' }}>
              {ingredient.conc}
            </div>
          </div>
          <div style={{ backgroundColor: 'var(--c-canvas)', padding: '14px', borderRadius: '8px', border: '1px solid var(--c-border-subtle)' }}>
            <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Độ pH hoạt động</div>
            <div style={{ fontSize: '15px', fontWeight: 800, color: 'var(--c-primary)', marginTop: '4px', fontFamily: 'var(--font-mono)' }}>
              {ingredient.phRange || 'pH 4.5 - 6.5'}
            </div>
          </div>
          <div style={{ backgroundColor: 'var(--c-canvas)', padding: '14px', borderRadius: '8px', border: '1px solid var(--c-border-subtle)' }}>
            <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Cấp độ an toàn EWG</div>
            <div style={{ fontSize: '15px', fontWeight: 800, color: 'var(--c-safe-green)', marginTop: '4px', fontFamily: 'var(--font-mono)' }}>
              1 - 2 (Lành tính)
            </div>
          </div>
        </div>

        {/* Combinations Matrix (Nên & Không nên) */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: '16px',
          marginBottom: '28px'
        }}>
          <div style={{ backgroundColor: 'var(--c-safe-green-bg)', padding: '16px', borderRadius: '8px', border: '1px solid var(--c-safe-green-border)' }}>
            <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--c-safe-green)', display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px' }}>
              <CheckCircle2 size={15} />
              <span>KẾT HỢP HOÀN HẢO VỚI:</span>
            </div>
            <div style={{ fontSize: '12.5px', color: 'var(--c-primary)', lineHeight: 1.5 }}>
              {ingredient.bestWith || 'Hyaluronic Acid, B5 Panthenol, Ceramide và Kem chống nắng phổ rộng.'}
            </div>
          </div>

          <div style={{ backgroundColor: '#FEF2F2', padding: '16px', borderRadius: '8px', border: '1px solid #FECACA' }}>
            <div style={{ fontSize: '12px', fontWeight: 700, color: '#DC2626', display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px' }}>
              <AlertTriangle size={15} />
              <span>CẦN LƯU Ý KHI DÙNG VỚI:</span>
            </div>
            <div style={{ fontSize: '12.5px', color: 'var(--c-primary)', lineHeight: 1.5 }}>
              {ingredient.cautionWith || 'Tránh dùng chung cùng thời điểm với AHA nồng độ cao hoặc cồn khô làm rát da.'}
            </div>
          </div>
        </div>

        {/* Footer CTA */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
          <button
            onClick={onClose}
            className="btn-luxury-outline"
            style={{ fontSize: '13px', padding: '10px 20px' }}
          >
            Đóng
          </button>
          <button
            onClick={() => {
              onClose();
              onNavigate(`products?search=${encodeURIComponent(ingredient.name.split(' ')[0])}`);
            }}
            className="btn-luxury-gold"
            style={{ fontSize: '13px', padding: '10px 24px', display: 'flex', alignItems: 'center', gap: '6px' }}
          >
            <span>Xem Mỹ Phẩm Chứa {ingredient.name.split(' ')[0]}</span>
            <ArrowRight size={14} />
          </button>
        </div>
      </div>
    </div>
  );
};
