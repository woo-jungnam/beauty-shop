import React from 'react';
import { Ticket, Heart, Check, X } from 'lucide-react';

export const ToastNotification = ({ toasts = [], onDismiss }) => {
  if (!toasts || toasts.length === 0) return null;

  return (
    <div style={{
      position: 'fixed',
      top: '24px',
      right: '24px',
      zIndex: 1300,
      display: 'flex',
      flexDirection: 'column',
      gap: '10px',
      pointerEvents: 'none'
    }}>
      {toasts.map(toast => (
        <div
          key={toast.id}
          style={{
            pointerEvents: 'auto',
            minWidth: '320px',
            maxWidth: '400px',
            backgroundColor: '#FFFFFF',
            border: '1px solid var(--c-border)',
            borderRadius: 'var(--radius-md)',
            boxShadow: '0 12px 32px rgba(15, 23, 42, 0.15)',
            padding: '14px 18px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '12px',
            position: 'relative',
            overflow: 'hidden',
            animation: 'fadeInUp 0.3s cubic-bezier(0.16, 1, 0.3, 1)'
          }}
        >
          {/* Toast Icon */}
          <div style={{
            width: '32px',
            height: '32px',
            borderRadius: '50%',
            backgroundColor: toast.type === 'coupon'
              ? 'var(--c-gold-light)'
              : (toast.type === 'wishlist' ? '#FEE2E2' : 'var(--c-safe-green-bg)'),
            color: toast.type === 'coupon'
              ? 'var(--c-gold)'
              : (toast.type === 'wishlist' ? '#EF4444' : 'var(--c-safe-green)'),
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            {toast.type === 'coupon' && <Ticket size={16} />}
            {toast.type === 'wishlist' && <Heart size={16} fill="#EF4444" />}
            {(!toast.type || toast.type === 'cart' || toast.type === 'success') && <Check size={16} />}
          </div>

          {/* Toast Content */}
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--c-primary)' }}>
              {toast.title}
            </div>
            <div style={{ fontSize: '12px', color: 'var(--c-text-muted)', marginTop: '2px', lineHeight: 1.4 }}>
              {toast.message}
            </div>
          </div>

          {/* Close button */}
          <button
            onClick={() => onDismiss(toast.id)}
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--c-text-light)',
              cursor: 'pointer',
              padding: '2px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <X size={15} />
          </button>

          {/* Countdown indicator line at bottom */}
          <div style={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            height: '3px',
            background: toast.type === 'coupon' ? 'var(--c-gold)' : 'var(--c-safe-green)',
            animation: 'toastProgress 3.5s linear forwards'
          }} />
        </div>
      ))}
    </div>
  );
};
