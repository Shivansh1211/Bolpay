import { useTranslation } from '../i18n';

const Button = ({ 
  children, 
  variant = 'primary', 
  size = 'md', 
  disabled = false, 
  loading = false,
  onClick,
  className = '',
  ...props 
}) => {
  const { t } = useTranslation();
  const baseStyles = 'font-semibold rounded-sm min-h-target transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2';
  
  const variants = {
    primary: 'bg-brand text-white hover:bg-brand-dark focus:ring-brand disabled:opacity-50 disabled:cursor-not-allowed',
    secondary: 'bg-secondary text-text-primary hover:bg-gray-100 focus:ring-gray-400 disabled:opacity-50',
    danger: 'bg-status-expired text-white hover:bg-red-700 focus:ring-red-500 disabled:opacity-50',
    accent: 'bg-accent text-white hover:bg-blue-700 focus:ring-blue-500 disabled:opacity-50',
    ghost: 'bg-transparent text-text-primary hover:bg-gray-100 focus:ring-gray-400 disabled:opacity-50',
  };
  
  const sizes = {
    sm: 'px-3 py-2 text-small',
    md: 'px-4 py-3 text-body',
    lg: 'px-6 py-4 text-h3',
    xl: 'px-8 py-5 text-h2',
  };
  
  return (
    <button
      className={`${baseStyles} ${variants[variant]} ${sizes[size]} ${className}`}
      disabled={disabled || loading}
      onClick={onClick}
      {...props}
    >
      {loading ? (
        <span className="flex items-center justify-center gap-2">
          <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
          {t('processing')}
        </span>
      ) : (
        children
      )}
    </button>
  );
};

export default Button;
