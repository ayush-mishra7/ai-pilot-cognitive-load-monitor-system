import { useRef, useMemo } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import * as THREE from 'three';

/* ─── Animated wireframe grid with slow radar sweep ─── */

function GridPlane() {
  const meshRef = useRef();
  const sweepRef = useRef(0);

  const geometry = useMemo(() => new THREE.PlaneGeometry(40, 40, 60, 60), []);

  useFrame((_, delta) => {
    if (!meshRef.current) return;
    sweepRef.current += delta * 0.15;
    meshRef.current.rotation.x = -Math.PI / 2.2;
    meshRef.current.position.y = -1.5;
  });

  return (
    <mesh ref={meshRef} geometry={geometry}>
      <meshBasicMaterial
        color="#1a1a1a"
        wireframe
        transparent
        opacity={0.06}
      />
    </mesh>
  );
}

function RadarSweep() {
  const ref = useRef();
  const geo = useMemo(() => {
    const shape = new THREE.RingGeometry(0.1, 8, 64, 1, 0, Math.PI / 6);
    return shape;
  }, []);

  useFrame((_, delta) => {
    if (!ref.current) return;
    ref.current.rotation.z += delta * 0.4;
  });

  return (
    <mesh ref={ref} position={[0, -1.5, 0]} rotation={[-Math.PI / 2.2, 0, 0]}>
      <primitive object={geo} attach="geometry" />
      <meshBasicMaterial
        color="#6B8E23"
        transparent
        opacity={0.04}
        side={THREE.DoubleSide}
      />
    </mesh>
  );
}

export default function TechGrid() {
  return (
    <div
      className="fixed inset-0 -z-10"
      style={{ pointerEvents: 'none' }}
    >
      <Canvas
        camera={{ position: [0, 6, 12], fov: 45 }}
        dpr={1}
        gl={{
          antialias: false,
          alpha: true,
          powerPreference: 'low-power',
        }}
        frameloop="always"
        style={{ background: 'transparent' }}
      >
        <GridPlane />
        <RadarSweep />
      </Canvas>
    </div>
  );
}
