import React, { useState, useEffect } from 'react';
import { ArrowUp } from 'lucide-react';

export const ScrollProgressTop = () => {
  const [scrollProgress, setScrollProgress] = useState(0);
  const [isVisible, setIsVisible] = useState(false);
  const [isHovered, setIsHovered] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      const totalHeight = document.documentElement.scrollHeight - window.innerHeight;
      if (totalHeight <= 0) return;
      
      const currentScroll = window.scrollY;
      const progress = Math.min(100, Math.max(0, Math.round((currentScroll / totalHeight) * 100)));
      setScrollProgress(progress);
      setIsVisible(currentScroll > 320);
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const scrollToTop = () => {
    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  };

  if (!isVisible) return null;

  // SVG circular calculation
  const radius = 20;
  const circumference = 2 * Math.PI * radius;
  const strokeDashoffset = circumference - (scrollProgress / 100) * circumference;

  return (
    <div
      style={{
        position: 'fixed',
        bottom: '28px',
        right: '28px',
        zIndex: 990,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '6px'
      }}
    >
      {/* Tooltip on hover */}
      {isHovered && (
        <div style={{
          backgroundColor: 'var(--c-primary)',
          color: '#FFFFFF',
          fontSize: '11px',
          fontWeight: 600,
          padding: '4px 10px',
          borderRadius: '6px',
          border: '1px solid var(--c-gold-border)',
          boxShadow: '0 4px 16px rgba(45, 34, 30, 0.25)',
          whiteSpace: 'nowrap',
          animation: 'fadeInUp 0.2s ease',
          pointerEvents: 'none'
        }}>
          Lên đầu trang ({scrollProgress}%)
        </div>
      )}

      <button
        onClick={scrollToTop}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        aria-label="Cuộn lên đầu trang"
        style={{
          position: 'relative',
          width: '50px',
          height: '50px',
          borderRadius: '50%',
          backgroundColor: '#FFFFFF',
          border: 'none',
          boxShadow: '0 6px 20px rgba(0, 0, 0, 0.15)',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: 'transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.25s ease',
          transform: isHovered ? 'translateY(-3px) scale(1.05)' : 'translateY(0) scale(1)',
          padding: 0
        }}
      >
        {/* SVG Progress Ring */}
        <svg
          width="50"
          height="50"
          viewBox="0 0 48 48"
          style={{ position: 'absolute', transform: 'rotate(-90deg)' }}
        >
          {/* Background circle */}
          <circle
            cx="24"
            cy="24"
            r={radius}
            fill="transparent"
            stroke="var(--c-border-subtle)"
            strokeWidth="3.5"
          />
          {/* Progress circle */}
          <circle
            cx="24"
            cy="24"
            r={radius}
            fill="transparent"
            stroke="var(--c-gold)"
            strokeWidth="3.5"
            strokeDasharray={circumference}
            strokeDashoffset={strokeDashoffset}
            strokeLinecap="round"
            style={{ transition: 'stroke-dashoffset 0.15s ease' }}
          />
        </svg>

        {/* Center Icon */}
        <div style={{
          position: 'relative',
          zIndex: 2,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--c-primary)'
        }}>
          <ArrowUp size={18} />
        </div>
      </button>
    </div>
  );
};
