import React, { useState, useRef, useCallback } from 'react';
import { Microscope, User, MoveHorizontal } from 'lucide-react';

export const BeforeAfterSlider = ({
  beforeImage,
  afterImage,
  caseTitle,
  patient,
  stats,
  routine,
  tag = 'Khảo Nghiệm 3D Visia'
}) => {
  const [sliderPos, setSliderPos] = useState(50);
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef(null);

  const handleMove = useCallback((clientX) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const x = clientX - rect.left;
    const percentage = Math.max(0, Math.min(100, (x / rect.width) * 100));
    setSliderPos(percentage);
  }, []);

  const handleMouseDown = () => setIsDragging(true);
  const handleMouseUp = () => setIsDragging(false);

  const handleMouseMove = (e) => {
    if (!isDragging) return;
    handleMove(e.clientX);
  };

  const handleTouchMove = (e) => {
    if (e.touches && e.touches[0]) {
      handleMove(e.touches[0].clientX);
    }
  };

  return (
    <div className="clinical-case-card" style={{ padding: '24px', display: 'flex', flexDirection: 'column' }}>
      {/* Card Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
        <span className="badge-dermatology safe" style={{ fontSize: '11px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
          <Microscope size={13} />
          <span>{tag}</span>
        </span>
        <span style={{ fontSize: '11px', color: 'var(--c-text-light)', fontWeight: 600 }}>
          Trượt để so sánh hiệu quả
        </span>
      </div>

      <h3 style={{ fontSize: '16px', fontWeight: 700, margin: '0 0 6px 0', color: 'var(--c-primary)' }}>
        {caseTitle}
      </h3>
      <div style={{ fontSize: '12px', color: 'var(--c-gold-hover)', fontWeight: 600, marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '6px' }}>
        <User size={13} />
        <span>{patient}</span>
      </div>

      {/* Interactive Split Comparison Viewport */}
      <div
        ref={containerRef}
        onMouseMove={handleMouseMove}
        onMouseDown={handleMouseDown}
        onMouseUp={handleMouseUp}
        onTouchMove={handleTouchMove}
        style={{
          position: 'relative',
          width: '100%',
          height: '240px',
          borderRadius: 'var(--radius-sm)',
          overflow: 'hidden',
          cursor: 'ew-resize',
          userSelect: 'none',
          boxShadow: '0 4px 14px rgba(0, 0, 0, 0.08)',
          marginBottom: '16px'
        }}
      >
        {/* Background Layer: AFTER IMAGE (Full width) */}
        <img
          src={afterImage}
          alt="Sau điều trị"
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            objectFit: 'cover'
          }}
        />

        {/* Foreground Layer: BEFORE IMAGE (Clipped by slider position) */}
        <div style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          clipPath: `inset(0 ${100 - sliderPos}% 0 0)`,
          overflow: 'hidden'
        }}>
          <img
            src={beforeImage}
            alt="Trước điều trị"
            style={{
              width: '100%',
              height: '100%',
              objectFit: 'cover'
            }}
          />
        </div>

        {/* Draggable Divider Line */}
        <div style={{
          position: 'absolute',
          top: 0,
          bottom: 0,
          left: `${sliderPos}%`,
          width: '3px',
          backgroundColor: '#FFFFFF',
          boxShadow: '0 0 8px rgba(0,0,0,0.5)',
          transform: 'translateX(-50%)',
          zIndex: 10,
          pointerEvents: 'none'
        }}>
          {/* Circular Divider Handle */}
          <div style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            width: '36px',
            height: '36px',
            borderRadius: '50%',
            backgroundColor: 'var(--c-gold)',
            color: '#FFFFFF',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
            border: '2px solid #FFFFFF'
          }}>
            <MoveHorizontal size={16} />
          </div>
        </div>

        {/* Floating Labels */}
        <div style={{
          position: 'absolute',
          bottom: '10px',
          left: '10px',
          backgroundColor: 'rgba(15, 23, 42, 0.75)',
          backdropFilter: 'blur(4px)',
          color: '#FFFFFF',
          fontSize: '10.5px',
          fontWeight: 700,
          padding: '3px 8px',
          borderRadius: '4px',
          pointerEvents: 'none',
          zIndex: 5
        }}>
          Trước phác đồ
        </div>

        <div style={{
          position: 'absolute',
          bottom: '10px',
          right: '10px',
          backgroundColor: 'rgba(5, 150, 105, 0.88)',
          backdropFilter: 'blur(4px)',
          color: '#FFFFFF',
          fontSize: '10.5px',
          fontWeight: 700,
          padding: '3px 8px',
          borderRadius: '4px',
          pointerEvents: 'none',
          zIndex: 5
        }}>
          Sau điều trị
        </div>
      </div>

      {/* Preset Quick Snap Controls */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '14px' }}>
        <button
          onClick={() => setSliderPos(0)}
          style={{
            flex: 1,
            padding: '5px 0',
            fontSize: '11px',
            fontWeight: 600,
            borderRadius: '6px',
            border: '1px solid var(--c-border)',
            backgroundColor: sliderPos === 0 ? 'var(--c-primary)' : '#FFFFFF',
            color: sliderPos === 0 ? '#FFFFFF' : 'var(--c-text-muted)',
            cursor: 'pointer'
          }}
        >
          Xem Sau
        </button>
        <button
          onClick={() => setSliderPos(50)}
          style={{
            flex: 1,
            padding: '5px 0',
            fontSize: '11px',
            fontWeight: 600,
            borderRadius: '6px',
            border: '1px solid var(--c-border)',
            backgroundColor: sliderPos === 50 ? 'var(--c-gold)' : '#FFFFFF',
            color: sliderPos === 50 ? '#FFFFFF' : 'var(--c-text-muted)',
            cursor: 'pointer'
          }}
        >
          So Sánh 50/50
        </button>
        <button
          onClick={() => setSliderPos(100)}
          style={{
            flex: 1,
            padding: '5px 0',
            fontSize: '11px',
            fontWeight: 600,
            borderRadius: '6px',
            border: '1px solid var(--c-border)',
            backgroundColor: sliderPos === 100 ? 'var(--c-primary)' : '#FFFFFF',
            color: sliderPos === 100 ? '#FFFFFF' : 'var(--c-text-muted)',
            cursor: 'pointer'
          }}
        >
          Xem Trước
        </button>
      </div>

      {/* Statistical Metric Badge */}
      <div style={{
        backgroundColor: 'var(--c-canvas)',
        padding: '10px 14px',
        borderRadius: '6px',
        marginBottom: '10px'
      }}>
        <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Chỉ số khảo nghiệm lâm sàng:</div>
        <div style={{ fontSize: '13px', fontWeight: 800, color: 'var(--c-safe-green)', marginTop: '2px', fontFamily: 'var(--font-mono)' }}>
          {stats}
        </div>
      </div>

      {/* Applied Regimen Details */}
      <div style={{ fontSize: '11.5px', color: 'var(--c-text-muted)', marginTop: 'auto' }}>
        <strong>Phác đồ ứng dụng:</strong> {routine}
      </div>
    </div>
  );
};
