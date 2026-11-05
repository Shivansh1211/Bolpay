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
    default: 'bg-secondary border border-gray-300',
    brand: 'bg-brand text-white',
    secondary: 'bg-secondary text-text-primary',
    status: 'bg-secondary border-y border-gray-300',
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
    sm: 'shadow-none',
    md: 'shadow-none',
    lg: 'shadow-none',
    xl: 'shadow-none',
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
