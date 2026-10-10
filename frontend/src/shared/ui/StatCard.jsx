import React from 'react';

export const StatCard = ({
  label,
  value,
  meta,
  icon: Icon,
  className = '',
  tone = 'violet',
}) => {
  return (
    <div className={`stat-card stat-card-${tone} ${className}`}>
      <div className="stat-header">
        <span className="stat-label">{label}</span>
        {Icon && (
          <div
            className="stat-icon"
          >
            <Icon size={20} aria-hidden="true" />
          </div>
        )}
      </div>
      <div className="stat-value metric-value">{value}</div>
      {meta && <div className="stat-meta">{meta}</div>}
    </div>
  );
};
