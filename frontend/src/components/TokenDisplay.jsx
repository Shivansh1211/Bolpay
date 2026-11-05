import React from 'react';

const TokenDisplay = ({ token, size = 'lg' }) => {
  const sizes = {
    sm: 'text-h2',
    md: 'text-h1',
    lg: 'text-6xl',
    xl: 'text-7xl',
  };

  return (
    <div className={`font-bold text-text-primary tracking-widest ${sizes[size]}`}>
      {token}
    </div>
  );
};

export default TokenDisplay;
