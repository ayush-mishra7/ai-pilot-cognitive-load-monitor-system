import { motion } from 'framer-motion';

/**
 * Recommendation card displayed in the live feed.
 */
export default function RecommendationCard({ recommendation, index = 0 }) {
  const severityStyles = {
    INFO: { border: 'border-[#6B8E23]/30', icon: 'i', iconBg: 'bg-[#6B8E23]/10', iconColor: 'text-[#6B8E23]' },
    WARNING: { border: 'border-yellow-400/40', icon: '!', iconBg: 'bg-yellow-400/10', iconColor: 'text-yellow-600' },
    CRITICAL: { border: 'border-red-500/40', icon: '!!', iconBg: 'bg-red-500/10', iconColor: 'text-red-600' },
  };

  const style = severityStyles[recommendation?.severity] || severityStyles.INFO;

  return (
    <motion.div
      className={`glass-card p-4 ${style.border} border flex items-start gap-3`}
      initial={{ opacity: 0, x: 30 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: index * 0.08, duration: 0.3 }}
    >
      <div
        className={`w-8 h-8 rounded-lg ${style.iconBg} flex items-center justify-center flex-shrink-0`}
      >
        <span className={`font-bold text-sm ${style.iconColor}`}>
          {style.icon}
        </span>
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-high-contrast truncate">
          {recommendation?.type?.replace(/_/g, ' ') || 'Recommendation'}
        </p>
        <p className="text-xs text-gray-500 mt-1 leading-relaxed">
          {recommendation?.message || 'No details available'}
        </p>
      </div>
      <span className="text-[10px] font-medium text-gray-400 flex-shrink-0">
        {recommendation?.severity || 'INFO'}
      </span>
    </motion.div>
  );
}
