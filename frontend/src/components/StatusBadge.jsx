import React from 'react';

const StatusBadge = ({ status, size = 'md' }) => {
  const statusConfig = {
    PENDING: {
      colour: 'bg-status-paying',
      text: 'Paying',
      icon: (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      ),
    },
    PAID: {
      colour: 'bg-status-paid',
      text: 'Paid',
      icon: (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
        </svg>
      ),
    },
    EXPIRED: {
      colour: 'bg-status-expired',
      text: 'Expired',
      icon: (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      ),
    },
    LATE_PAID: {
      colour: 'bg-status-late',
      text: 'Late',
      icon: (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
      ),
    },
  };

  const config = statusConfig[status] || statusConfig.PENDING;
  
  const sizes = {
    sm: 'px-2 py-1 text-tiny',
    md: 'px-3 py-1.5 text-small',
    lg: 'px-4 py-2 text-body',
  };

  return (
    <span className={`inline-flex items-center gap-1.5 rounded-sm ${config.colour} text-white ${sizes[size]}`}>
      {config.icon}
      {config.text}
    </span>
  );
};

export default StatusBadge;
