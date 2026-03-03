/**
 * Reusable panel wrapper. Supports glass (public) and av (console) modes.
 */
export default function GlassPanel({ children, title, className = '', variant = 'glass' }) {
  if (variant === 'av') {
    return (
      <div className={`av-panel ${className}`}>
        {title && <div className="av-header">{title}</div>}
        <div className="p-3">{children}</div>
      </div>
    );
  }

  return (
    <div className={`glass p-6 ${className}`}>
      {title && (
        <h3 className="text-sm font-bold tracking-widest uppercase text-gray-500 mb-4">
          {title}
        </h3>
      )}
      {children}
    </div>
  );
}
