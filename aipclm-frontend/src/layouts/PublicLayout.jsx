import { Outlet } from 'react-router-dom';
import { motion } from 'framer-motion';

export default function PublicLayout() {
  return (
    <motion.div
      className="min-h-screen w-full"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.4 }}
    >
      <Outlet />
    </motion.div>
  );
}
