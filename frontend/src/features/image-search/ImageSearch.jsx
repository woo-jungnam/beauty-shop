import React, { useEffect, useRef, useState } from 'react';
import {
  Camera,
  Search,
  RefreshCw,
  UploadCloud,
  Image as ImageIcon,
  CheckCircle2,
  AlertCircle,
  HelpCircle,
  ArrowRight,
  ArrowLeft,
  X,
} from 'lucide-react';
import { ProductCard } from '../../customer/components/product/ProductCard';
import { searchProductsByImage } from './imageSearchApi';
import './image-search.css';

const MAX_BYTES = 5 * 1024 * 1024;
const TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/avif']);

export function ImageSearch({ onNavigate }) {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [isDragging, setIsDragging] = useState(false);

  // Webcam capture state (for Desktop web)
  const [isWebcamOpen, setIsWebcamOpen] = useState(false);
  const [webcamError, setWebcamError] = useState('');
  const videoRef = useRef(null);
  const streamRef = useRef(null);

  const pending = useRef(null);
  const version = useRef(0);
  const fileInputRef = useRef(null);
  const cameraInputRef = useRef(null);
  const previewUrl = useRef('');

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      version.current += 1;
      pending.current?.abort();
      if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
      stopWebcam();
    };
  }, []);

  // Clipboard paste support
  useEffect(() => {
    const handlePaste = (e) => {
      const items = e.clipboardData?.items;
      if (!items) return;
      for (let i = 0; i < items.length; i++) {
        if (items[i].type.startsWith('image/')) {
          const pastedFile = items[i].getAsFile();
          if (pastedFile) {
            chooseFile(pastedFile);
            break;
          }
        }
      }
    };
    window.addEventListener('paste', handlePaste);
    return () => window.removeEventListener('paste', handlePaste);
  }, []);

  function chooseFile(selected, autoSearch = true) {
    version.current += 1;
    pending.current?.abort();
    pending.current = null;
    setLoading(false);
    setResult(null);
    setError('');
    setFile(null);
    stopWebcam();

    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = '';
    setPreview('');

    if (!selected) return;

    if (!TYPES.has(selected.type) && !selected.type.startsWith('image/')) {
      setError('Vui lòng chọn ảnh JPEG, PNG, WebP hoặc AVIF.');
      return;
    }
    if (selected.size === 0 || selected.size > MAX_BYTES) {
      setError('Ảnh cần có dữ liệu và dung lượng không vượt quá 5 MiB.');
      return;
    }

    setFile(selected);
    previewUrl.current = URL.createObjectURL(selected);
    setPreview(previewUrl.current);

    if (autoSearch) {
      triggerSearch(selected);
    }
  }

  async function triggerSearch(targetFile = file) {
    if (!targetFile || pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    const current = ++version.current;
    setLoading(true);
    setError('');
    setResult(null);

    try {
      const data = await searchProductsByImage(targetFile, { minSimilarity: 0.65 }, controller.signal);
      if (current === version.current) {
        setResult(data);
      }
    } catch (err) {
      if (current === version.current && !controller.signal.aborted) {
        setError(err.message || 'Không tìm được sản phẩm khớp với hình ảnh. Vui lòng thử lại.');
      }
    } finally {
      if (current === version.current) {
        pending.current = null;
        setLoading(false);
      }
    }
  }

  // Drag and drop handlers
  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    const dropped = e.dataTransfer.files?.[0];
    if (dropped) {
      chooseFile(dropped);
    }
  };

  // Webcam functions for Web Desktop
  const startWebcam = async () => {
    setError('');
    setWebcamError('');
    setIsWebcamOpen(true);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: false,
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        videoRef.current.play();
      }
    } catch (err) {
      console.warn('Lỗi bật camera:', err);
      setWebcamError('Không thể mở Camera của máy. Vui lòng cấp quyền truy cập camera trong trình duyệt hoặc tải ảnh từ tệp.');
    }
  };

  const stopWebcam = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    setIsWebcamOpen(false);
    setWebcamError('');
  };

  const captureWebcamPhoto = () => {
    if (!videoRef.current) return;
    const video = videoRef.current;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    canvas.toBlob(
      (blob) => {
        if (blob) {
          const capturedFile = new File([blob], `camera-${Date.now()}.jpg`, { type: 'image/jpeg' });
          stopWebcam();
          chooseFile(capturedFile);
        }
      },
      'image/jpeg',
      0.92
    );
  };

  const getConfidenceLevel = (similarity) => {
    const score = Math.round((similarity || 0) * 100);
    if (score >= 85) return { label: 'Trùng khớp cao', color: '#15803d', bg: '#f0fdf4', border: '#bbf7d0', score };
    if (score >= 70) return { label: 'Tương đồng phù hợp', color: '#8c2a47', bg: '#fff0f3', border: '#f6d5de', score };
    return { label: 'Gợi ý tham khảo', color: '#6b7280', bg: '#f3f4f6', border: '#e5e7eb', score };
  };

  return (
    <div className="customer-container image-search-page-v2">
      {/* BREADCRUMB */}
      <nav className="image-search-breadcrumb" aria-label="Đường dẫn">
        <button type="button" onClick={() => onNavigate?.('products')}>
          <ArrowLeft size={14} /> Danh mục sản phẩm
        </button>
        <span aria-hidden="true">/</span>
        <span>Tìm kiếm bằng hình ảnh</span>
      </nav>

      {/* HERO TITLE */}
      <header className="image-search-header-hero">
        <div className="image-search-title-badge">
          <Camera size={15} /> Công nghệ trích xuất thị giác CLIP AI
        </div>
        <h1>Tìm kiếm sản phẩm bằng hình ảnh</h1>
        <p>
          Chụp ảnh bao bì hoặc tải lên hình ảnh sản phẩm để tìm kiếm trực tiếp trong kho mỹ phẩm BeautyShop.
          Tối ưu chính xác với thân chai, tuýp kem và nhãn mác.
        </p>
      </header>

      {/* MAIN UPLOAD & CAMERA WORKSPACE */}
      <section className="image-search-workspace">
        {/* HIDDEN INPUTS */}
        {/* 1. Mobile Camera direct trigger */}
        <input
          ref={cameraInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          style={{ display: 'none' }}
          onChange={(e) => chooseFile(e.target.files?.[0])}
        />
        {/* 2. File picker trigger */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp,image/avif"
          style={{ display: 'none' }}
          onChange={(e) => chooseFile(e.target.files?.[0])}
        />

        {/* WEBCAM MODAL / STREAM OVERLAY (DESKTOP) */}
        {isWebcamOpen && (
          <div className="image-search-webcam-card">
            <div className="image-search-webcam-top">
              <strong>Camera trực tiếp</strong>
              <button type="button" className="image-search-btn-icon" onClick={stopWebcam} aria-label="Đóng camera">
                <X size={18} />
              </button>
            </div>

            {webcamError ? (
              <div className="image-search-webcam-error">
                <AlertCircle size={20} />
                <span>{webcamError}</span>
              </div>
            ) : (
              <div className="image-search-video-container">
                <video ref={videoRef} autoPlay playsInline muted className="image-search-video-feed" />
                <div className="image-search-video-reticle" />
              </div>
            )}

            <div className="image-search-webcam-actions">
              <button type="button" className="btn-luxury-secondary" onClick={stopWebcam}>
                Hủy bỏ
              </button>
              {!webcamError && (
                <button type="button" className="btn-luxury-primary" onClick={captureWebcamPhoto}>
                  <Camera size={18} /> Chụp ảnh này
                </button>
              )}
            </div>
          </div>
        )}

        {/* DROPZONE / PREVIEW AREA */}
        {!isWebcamOpen && (
          <div className="image-search-card-main">
            {!preview ? (
              <div
                className={`image-search-dropzone ${isDragging ? 'is-dragging' : ''}`}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
              >
                <div className="image-search-dropzone-icon-ring">
                  <UploadCloud size={32} />
                </div>
                <h3>Kéo thả ảnh sản phẩm vào đây</h3>
                <p>Hoặc dán ảnh từ bộ nhớ tạm (Ctrl + V). Hỗ trợ JPEG, PNG, WebP, tối đa 5 MB.</p>

                {/* DUAL ACTION BUTTONS (MOBILE & DESKTOP) */}
                <div className="image-search-action-group">
                  <button
                    type="button"
                    className="image-search-action-btn primary"
                    onClick={() => {
                      // On touch/mobile: open native camera directly
                      if (/Mobi|Android|iPhone|iPad/i.test(navigator.userAgent)) {
                        cameraInputRef.current?.click();
                      } else {
                        startWebcam();
                      }
                    }}
                  >
                    <Camera size={18} />
                    <span>Chụp ảnh bằng Camera</span>
                  </button>

                  <button
                    type="button"
                    className="image-search-action-btn secondary"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <ImageIcon size={18} />
                    <span>Chọn ảnh từ thiết bị</span>
                  </button>
                </div>
              </div>
            ) : (
              <div className="image-search-preview-card">
                <div className="image-search-preview-media">
                  <img src={preview} alt="Ảnh sản phẩm bạn chọn" />
                  <div className="image-search-preview-overlay">
                    <span className="image-search-filename">{file?.name || 'Ảnh đã chọn'}</span>
                    <button
                      type="button"
                      className="image-search-btn-change"
                      onClick={() => {
                        chooseFile(null);
                        if (fileInputRef.current) fileInputRef.current.value = '';
                        if (cameraInputRef.current) cameraInputRef.current.value = '';
                      }}
                    >
                      <RefreshCw size={14} /> Đổi ảnh khác
                    </button>
                  </div>
                </div>

                <div className="image-search-preview-controls">
                  <div className="image-search-preview-info">
                    <h4>Ảnh đã sẵn sàng</h4>
                    <p>Hệ thống tự động cắt viền chuẩn hóa tỷ lệ và trích xuất vector đặc trưng.</p>
                  </div>

                  <div className="image-search-preview-btns">
                    <button
                      type="button"
                      className="btn-luxury-primary"
                      disabled={loading}
                      onClick={() => triggerSearch(file)}
                    >
                      {loading ? (
                        <>
                          <RefreshCw size={16} className="animate-spin" /> Đang tìm kiếm…
                        </>
                      ) : (
                        <>
                          <Search size={16} /> Tìm lại sản phẩm
                        </>
                      )}
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </section>

      {/* ERROR NOTICE */}
      {error && (
        <div className="image-search-alert error" role="alert">
          <AlertCircle size={18} />
          <span>{error}</span>
          <button type="button" onClick={() => setError('')} aria-label="Đóng thông báo">
            <X size={16} />
          </button>
        </div>
      )}

      {/* SEARCHING ANIMATION */}
      {loading && (
        <div className="image-search-loading-state" role="status">
          <div className="image-search-spinner-ring">
            <RefreshCw size={24} className="animate-spin" />
          </div>
          <h3>Đang phân tích hình ảnh và đối soát kho sản phẩm...</h3>
          <p>Thuật toán trích xuất vector CLIP ViT-B/32 đang so khớp các đặc trưng hình học và nhãn mác.</p>
        </div>
      )}

      {/* RESULTS DISPLAY */}
      {!loading && result && (
        <section className="image-search-results-section" aria-live="polite">
          <div className="image-search-results-header">
            <div>
              <h2>Kết quả tìm kiếm sản phẩm</h2>
              <p>Danh sách sản phẩm tương đồng cao nhất được xếp theo độ khớp hình ảnh thực tế.</p>
            </div>
            <span className="image-search-results-count">
              {result.items?.length || 0} sản phẩm phù hợp
            </span>
          </div>

          {result.items && result.items.length > 0 ? (
            <div className="image-search-grid">
              {result.items.map((item, index) => {
                const conf = getConfidenceLevel(item.similarity);
                return (
                  <div key={item.product?.id || index} className="image-search-item-card">
                    {/* CONFIDENCE BADGE */}
                    <div
                      className="image-search-badge"
                      style={{ background: conf.bg, color: conf.color, borderColor: conf.border }}
                    >
                      <CheckCircle2 size={13} />
                      <span>{conf.label} ({conf.score}%)</span>
                    </div>

                    {/* PRODUCT CARD */}
                    <div className="image-search-product-wrapper">
                      <ProductCard product={item.product} onNavigate={onNavigate} fromSearch />
                    </div>

                    {/* STOCK STATUS & QUICK ACTION */}
                    <div className="image-search-item-footer">
                      <span className={`image-search-stock-tag ${item.inStock ? 'in-stock' : 'out-of-stock'}`}>
                        {item.inStock ? 'Còn hàng' : 'Tạm hết hàng'}
                      </span>
                      <button
                        type="button"
                        className="image-search-link-detail"
                        onClick={() => onNavigate(item.detailPath || `product/${item.product.id}`)}
                      >
                        Xem chi tiết <ArrowRight size={13} />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="image-search-empty-state">
              <div className="image-search-empty-icon">
                <HelpCircle size={32} />
              </div>
              <h3>Chưa tìm thấy sản phẩm trùng khớp đạt ngưỡng tin cậy</h3>
              <p>
                Ảnh tải lên có thể bị mờ, chụp ở góc quá hẹp hoặc không có trong kho hàng BeautyShop.
              </p>

              <div className="image-search-tips-card">
                <strong>Mẹo để tìm ảnh chính xác nhất:</strong>
                <ul>
                  <li>Chụp thẳng góc chính diện bao bì hoặc mặt trước chai lọ.</li>
                  <li>Đảm bảo đủ ánh sáng, rõ logo thương hiệu và tên sản phẩm.</li>
                  <li>Đặt sản phẩm trên nền sáng hoặc trơn màu (tránh nền quá nhiều chi tiết rối mắt).</li>
                </ul>
              </div>

              <div className="image-search-empty-actions">
                <button
                  type="button"
                  className="btn-luxury-primary"
                  onClick={() => {
                    chooseFile(null);
                    if (fileInputRef.current) fileInputRef.current.value = '';
                  }}
                >
                  <Camera size={16} /> Chụp hoặc tải ảnh khác
                </button>
                <button
                  type="button"
                  className="btn-luxury-secondary"
                  onClick={() => onNavigate('products')}
                >
                  Xem danh mục toàn bộ sản phẩm
                </button>
              </div>
            </div>
          )}
        </section>
      )}

      {/* PHOTO TIPS FOOTER */}
      {!result && !loading && (
        <section className="image-search-tips-section">
          <h3>Hướng dẫn chụp ảnh để tìm kiếm hiệu quả</h3>
          <div className="image-search-tips-grid">
            <div className="image-search-tip-card">
              <span className="tip-number">1</span>
              <h4>Góc chụp chính diện</h4>
              <p>Hướng camera thẳng vào mặt trước của sản phẩm để mô hình nhận diện rõ thân chai và nhãn hiệu.</p>
            </div>
            <div className="image-search-tip-card">
              <span className="tip-number">2</span>
              <h4>Ánh sáng rõ ràng</h4>
              <p>Tránh ánh đèn phản quang chói lóa vào mặt bóng của chai lọ hoặc vùng tối che khuất chữ in.</p>
            </div>
            <div className="image-search-tip-card">
              <span className="tip-number">3</span>
              <h4>Một sản phẩm duy nhất</h4>
              <p>Chụp tập trung vào một chai hoặc hộp mỹ phẩm để hệ thống xác định đúng đối tượng tìm kiếm.</p>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
