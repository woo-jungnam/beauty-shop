import React, { useId } from 'react';

export const Select = ({
  label,
  error,
  helperText,
  options = [],
  className = '',
  id,
  children,
  ...props
}) => {
  const generatedId = useId();
  const selectId = id || `select-${generatedId}`;
  const messageId = `${selectId}-message`;
  const description = [props['aria-describedby'], (error || helperText) && messageId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`form-group ${error ? 'has-error' : ''} ${className}`}>
      {label && (
        <label htmlFor={selectId} className="form-label">
          {label}
        </label>
      )}
      <select {...props} id={selectId} className="form-select" aria-invalid={error ? true : props['aria-invalid']} aria-describedby={description}>
        {children ||
          options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
      </select>
      {(error || helperText) && (
        <span id={messageId} className={`form-message ${error ? 'form-error' : 'form-helper'}`} style={{ fontSize: '12px', color: error ? 'var(--color-danger-600)' : 'var(--text-muted)', marginTop: '2px' }}>
          {error || helperText}
        </span>
      )}
    </div>
  );
};
