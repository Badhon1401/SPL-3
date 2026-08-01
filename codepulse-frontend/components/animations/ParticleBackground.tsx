"use client";

import { useEffect, useRef } from "react";

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  opacity: number;
  color: string;
  pulse: number;
  pulseSpeed: number;
}

const COLORS = ["#00f5ff", "#bf5fff", "#39ff14", "#ff2d78", "#ffffff"];

export function ParticleBackground() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const particlesRef = useRef<Particle[]>([]);
  const animFrameRef = useRef<number>(0);
  const mouseRef = useRef({ x: -999, y: -999 });

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d")!;

    const resize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
      initParticles();
    };

    const initParticles = () => {
      const count = Math.floor((canvas.width * canvas.height) / 18000);
      particlesRef.current = Array.from({ length: count }, () => ({
        x: Math.random() * canvas.width,
        y: Math.random() * canvas.height,
        vx: (Math.random() - 0.5) * 0.3,
        vy: (Math.random() - 0.5) * 0.3,
        size: Math.random() * 2 + 0.5,
        opacity: Math.random() * 0.5 + 0.1,
        color: COLORS[Math.floor(Math.random() * COLORS.length)],
        pulse: Math.random() * Math.PI * 2,
        pulseSpeed: 0.02 + Math.random() * 0.03,
      }));
    };

    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const particles = particlesRef.current;

      // Draw connections
      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const dx = particles[i].x - particles[j].x;
          const dy = particles[i].y - particles[j].y;
          const dist = Math.sqrt(dx * dx + dy * dy);
          if (dist < 100) {
            ctx.beginPath();
            ctx.moveTo(particles[i].x, particles[i].y);
            ctx.lineTo(particles[j].x, particles[j].y);
            ctx.strokeStyle = `rgba(0, 245, 255, ${(1 - dist / 100) * 0.08})`;
            ctx.lineWidth = 0.5;
            ctx.stroke();
          }
        }

        // Mouse interaction
        const mdx = particles[i].x - mouseRef.current.x;
        const mdy = particles[i].y - mouseRef.current.y;
        const mdist = Math.sqrt(mdx * mdx + mdy * mdy);
        if (mdist < 120) {
          particles[i].vx += (mdx / mdist) * 0.05;
          particles[i].vy += (mdy / mdist) * 0.05;
        }

        // Update
        particles[i].pulse += particles[i].pulseSpeed;
        const pulseOpacity = particles[i].opacity * (0.7 + 0.3 * Math.sin(particles[i].pulse));

        // Draw
        ctx.beginPath();
        ctx.arc(particles[i].x, particles[i].y, particles[i].size, 0, Math.PI * 2);
        ctx.fillStyle = particles[i].color.replace(")", `, ${pulseOpacity})`).replace("rgb", "rgba").replace("#", "");

        // Hex color to rgba
        const hex = particles[i].color;
        const r = parseInt(hex.slice(1, 3), 16);
        const g = parseInt(hex.slice(3, 5), 16);
        const b = parseInt(hex.slice(5, 7), 16);
        ctx.fillStyle = `rgba(${r}, ${g}, ${b}, ${pulseOpacity})`;
        ctx.fill();

        // Glow
        if (particles[i].size > 1.2) {
          ctx.shadowBlur = 8;
          ctx.shadowColor = particles[i].color;
          ctx.fill();
          ctx.shadowBlur = 0;
        }

        // Move
        particles[i].x += particles[i].vx;
        particles[i].y += particles[i].vy;

        // Dampen velocity
        particles[i].vx *= 0.99;
        particles[i].vy *= 0.99;

        // Wrap around
        if (particles[i].x < 0) particles[i].x = canvas.width;
        if (particles[i].x > canvas.width) particles[i].x = 0;
        if (particles[i].y < 0) particles[i].y = canvas.height;
        if (particles[i].y > canvas.height) particles[i].y = 0;
      }

      animFrameRef.current = requestAnimationFrame(draw);
    };

    const onMouse = (e: MouseEvent) => {
      mouseRef.current = { x: e.clientX, y: e.clientY };
    };

    resize();
    draw();
    window.addEventListener("resize", resize);
    window.addEventListener("mousemove", onMouse);

    return () => {
      cancelAnimationFrame(animFrameRef.current);
      window.removeEventListener("resize", resize);
      window.removeEventListener("mousemove", onMouse);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      id="particle-canvas"
      className="fixed inset-0 pointer-events-none z-0"
    />
  );
}
