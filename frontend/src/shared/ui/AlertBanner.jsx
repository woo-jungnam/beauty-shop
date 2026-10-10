import React from 'react';
import { AlertTriangle, CheckCircle, Info, XCircle } from 'lucide-react';

const alertIcons = { danger: XCircle, success: CheckCircle, info: Info, warning: AlertTriangle };

export const AlertBanner = ({
  type = 'warning',
  title,
  message,
  action,
  className = '',
}) => {
  const variant = alertIcons[type] ? type : 'warning';
  const Icon = alertIcons[variant];

  return (
    <div className={`alert-banner alert-banner-${variant} ${className}`} role={variant === 'danger' ? 'alert' : 'status'}>
      <span className="alert-icon"><Icon size={20} aria-hidden="true" /></span>
      <div className="alert-content">
        {title && <div className="alert-title">{title}</div>}
        <div className="alert-message">{message}</div>
      </div>
      {action && <div className="alert-action">{action}</div>}
    </div>
  );
};
