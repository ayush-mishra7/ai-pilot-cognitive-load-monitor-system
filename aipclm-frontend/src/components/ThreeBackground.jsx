import { useRef, useMemo } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Float, MeshTransmissionMaterial } from '@react-three/drei';
import * as THREE from 'three';

/* ─── Individual Floating Shape ─── */

function FloatingShape({ position, geometry, color, speed, rotationAxis }) {
  const ref = useRef();

  useFrame((_, delta) => {
    if (!ref.current) return;
    ref.current.rotation.x += delta * speed * rotationAxis[0];
    ref.current.rotation.y += delta * speed * rotationAxis[1];
    ref.current.rotation.z += delta * speed * rotationAxis[2];
  });

  return (
    <Float speed={1.2} rotationIntensity={0.3} floatIntensity={0.8}>
      <mesh ref={ref} position={position} geometry={geometry}>
        <meshPhysicalMaterial
          color={color}
          transparent
          opacity={0.35}
          roughness={0.15}
          metalness={0.1}
          clearcoat={1}
          clearcoatRoughness={0.1}
        />
      </mesh>
    </Float>
  );
}

/* ─── Scene with multiple shapes ─── */

function Scene() {
  const shapes = useMemo(() => {
    const olive = new THREE.Color('#6B8E23');
    const cyan = new THREE.Color('#00C2FF');
    const oliveLight = new THREE.Color('#8FBC3A');
    const cyanLight = new THREE.Color('#66D9FF');

    return [
      {
        position: [-3.5, 2, -4],
        geometry: new THREE.IcosahedronGeometry(0.9, 0),
        color: olive,
        speed: 0.3,
        rotationAxis: [1, 0.5, 0],
      },
      {
        position: [4, -1.5, -5],
        geometry: new THREE.OctahedronGeometry(0.7, 0),
        color: cyan,
        speed: 0.25,
        rotationAxis: [0, 1, 0.5],
      },
      {
        position: [-1, -3, -6],
        geometry: new THREE.TorusGeometry(0.6, 0.25, 16, 32),
        color: oliveLight,
        speed: 0.2,
        rotationAxis: [0.5, 0, 1],
      },
      {
        position: [2.5, 3, -3],
        geometry: new THREE.DodecahedronGeometry(0.5, 0),
        color: cyanLight,
        speed: 0.35,
        rotationAxis: [1, 1, 0],
      },
      {
        position: [-4, 0, -7],
        geometry: new THREE.TetrahedronGeometry(0.8, 0),
        color: cyan,
        speed: 0.15,
        rotationAxis: [0, 0.5, 1],
      },
      {
        position: [0, 2.5, -5],
        geometry: new THREE.SphereGeometry(0.4, 32, 32),
        color: olive,
        speed: 0.4,
        rotationAxis: [1, 0, 0.5],
      },
      {
        position: [3.5, -3, -4],
        geometry: new THREE.ConeGeometry(0.5, 1, 6),
        color: oliveLight,
        speed: 0.28,
        rotationAxis: [0.5, 1, 0.5],
      },
      {
        position: [-2, 1, -8],
        geometry: new THREE.TorusKnotGeometry(0.4, 0.15, 64, 8),
        color: cyanLight,
        speed: 0.18,
        rotationAxis: [0, 1, 1],
      },
    ];
  }, []);

  return (
    <>
      <ambientLight intensity={0.6} />
      <directionalLight position={[5, 5, 5]} intensity={0.8} color="#ffffff" />
      <pointLight position={[-3, 3, 2]} intensity={0.4} color="#6B8E23" />
      <pointLight position={[3, -2, 3]} intensity={0.3} color="#00C2FF" />

      {shapes.map((s, i) => (
        <FloatingShape key={i} {...s} />
      ))}
    </>
  );
}

/* ─── Exported Canvas Component ─── */

export default function ThreeBackground() {
  return (
    <div className="fixed inset-0 -z-10" style={{ pointerEvents: 'none' }}>
      <Canvas
        camera={{ position: [0, 0, 6], fov: 50 }}
        dpr={[1, 2]}
        gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
        style={{ background: 'transparent' }}
      >
        <Scene />
      </Canvas>
    </div>
  );
}
