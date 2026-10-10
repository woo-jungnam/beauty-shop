import React from 'react';

export const Badge = ({
  children,
  variant = 'default',
  className = '',
  icon: Icon,
}) => {
  return (
    <span className={`badge badge-${variant} ${className}`}>
      {Icon && <Icon size={12} aria-hidden="true" />}
      {children}
    </span>
  );
};
