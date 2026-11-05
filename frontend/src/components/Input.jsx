import React from 'react';

const Input = ({ 
  label, 
  error, 
  icon, 
  prefix, 
  suffix,
  className = '',
  ...props 
}) => {
  const baseStyles = 'w-full min-h-target px-4 py-3 border rounded-sm focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent text-body bg-secondary';
  const errorStyles = error ? 'border-status-expired focus:ring-status-expired' : 'border-gray-500';
  
  return (
    <div className="space-y-1">
      {label && (
        <label htmlFor={props.id} className="block text-small font-medium text-text-primary">
          {label}
        </label>
      )}
      <div className="relative">
        {prefix && (
          <span className="absolute left-4 top-1/2 -translate-y-1/2 text-text-secondary">
            {prefix}
          </span>
        )}
        {icon && (
          <span className="absolute left-4 top-1/2 -translate-y-1/2 text-text-secondary">
            {icon}
          </span>
        )}
        <input
          className={`${baseStyles} ${errorStyles} ${prefix || icon ? 'pl-12' : ''} ${suffix ? 'pr-12' : ''} ${className}`}
          {...props}
        />
        {suffix && (
          <span className="absolute right-4 top-1/2 -translate-y-1/2 text-text-secondary">
            {suffix}
          </span>
        )}
      </div>
      {error && (
        <p className="text-tiny text-status-expired">{error}</p>
      )}
    </div>
  );
};

export default Input;
