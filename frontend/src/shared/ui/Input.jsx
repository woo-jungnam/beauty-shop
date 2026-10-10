import React, { useId } from 'react';

export const Input = ({
  label,
  error,
  helperText,
  icon: Icon,
  className = '',
  id,
  style,
  ...props
}) => {
  const generatedId = useId();
  const inputId = id || `input-${generatedId}`;
  const messageId = `${inputId}-message`;
  const description = [props['aria-describedby'], (error || helperText) && messageId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`form-group ${error ? 'has-error' : ''} ${className}`}>
      {label && (
        <label htmlFor={inputId} className="form-label">
          {label}
        </label>
      )}
      <div className={`input-control ${Icon ? 'has-icon' : ''}`} style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
        {Icon && (
          <div
            className="input-leading-icon"
            style={{
              position: 'absolute',
              left: '12px',
              color: 'var(--color-primary-400)',
              display: 'flex',
              alignItems: 'center',
              pointerEvents: 'none',
            }}
          >
            <Icon size={17} aria-hidden="true" />
          </div>
        )}
        <input
          {...props}
          id={inputId}
          className="form-input"
          aria-invalid={error ? true : props['aria-invalid']}
          aria-describedby={description}
          style={{ paddingLeft: Icon ? '40px' : '14px', ...style }}
        />
      </div>
      {error ? (
        <span id={messageId} className="form-message form-error" style={{ fontSize: '12px', color: 'var(--color-danger-600)', fontWeight: 600, marginTop: '2px' }}>
          {error}
        </span>
      ) : helperText ? (
        <span id={messageId} className="form-message form-helper" style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>
          {helperText}
        </span>
      ) : null}
    </div>
  );
};
