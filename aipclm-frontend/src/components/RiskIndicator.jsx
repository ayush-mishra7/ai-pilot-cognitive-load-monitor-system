import { motion } from 'framer-motion';

const riskConfig = {
  LOW: {
    color: '#6B8E23',
    bg: 'rgba(107, 142, 35, 0.1)',
    glow: 'glow-low',
    label: 'LOW',
  },
  MEDIUM: {
    color: '#FFC107',
    bg: 'rgba(255, 193, 7, 0.1)',
    glow: 'glow-medium',
    label: 'MEDIUM',
  },
  HIGH: {
    color: '#FF6B35',
    bg: 'rgba(255, 107, 53, 0.1)',
    glow: 'glow-high',
    label: 'HIGH',
  },
  CRITICAL: {
    color: '#DC2626',
    bg: 'rgba(220, 38, 38, 0.1)',
    glow: 'glow-critical',
    label: 'CRITICAL',
  },
};

export default function RiskIndicator({ level = 'LOW' }) {
  const config = riskConfig[level] || riskConfig.LOW;

  return (
    <motion.div
      className={`glass-card p-6 flex flex-col items-center gap-3 ${config.glow}`}
      initial={{ scale: 0.9, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ type: 'spring', stiffness: 200, damping: 20 }}
    >
      <span className="text-xs font-semibold tracking-widest text-gray-500 uppercase">
        Risk Level
      </span>

      <motion.div
        className="w-20 h-20 rounded-full flex items-center justify-center"
        style={{ backgroundColor: config.bg, border: `2px solid ${config.color}` }}
        animate={{ scale: [1, 1.05, 1] }}
        transition={{ duration: 2, repeat: Infinity, ease: 'easeInOut' }}
      >
        <span
          className="text-xl font-black"
          style={{ color: config.color }}
        >
          {config.label}
        </span>
      </motion.div>

      <div
        className="w-full h-1 rounded-full mt-2"
        style={{ backgroundColor: `${config.color}30` }}
      >
        <motion.div
          className="h-full rounded-full"
          style={{ backgroundColor: config.color }}
          initial={{ width: 0 }}
          animate={{
            width:
              level === 'LOW'
                ? '25%'
                : level === 'MEDIUM'
                ? '50%'
                : level === 'HIGH'
                ? '75%'
                : '100%',
          }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
        />
      </div>
    </motion.div>
  );
}
