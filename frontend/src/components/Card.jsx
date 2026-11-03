import React from 'react';

const Card = ({ 
  children, 
  variant = 'default', 
  padding = 'md', 
  shadow = 'md',
  className = '',
  ...props 
}) => {
  const variants = {
    default: 'bg-white border border-gray-200',
    brand: 'bg-brand text-white',
    secondary: 'bg-secondary text-text-primary',
    status: 'bg-gray-50',
  };
  
  const paddings = {
    none: 'p-0',
    sm: 'p-sm',
    md: 'p-md',
    lg: 'p-lg',
    xl: 'p-xl',
  };
  
  const shadows = {
    none: 'shadow-none',
    sm: 'shadow-sm',
    md: 'shadow-md',
    lg: 'shadow-lg',
    xl: 'shadow-xl',
  };
  
  return (
    <div
      className={`${variants[variant]} ${paddings[padding]} ${shadows[shadow]} rounded-md ${className}`}
      {...props}
    >
      {children}
    </div>
  );
};

export default Card;
