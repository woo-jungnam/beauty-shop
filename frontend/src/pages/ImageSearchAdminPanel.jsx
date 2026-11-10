import React, { useEffect, useState, useRef, useCallback } from 'react';
import {
  Camera,
  RefreshCw,
  Layers,
  Database,
  CheckCircle2,
  AlertCircle,
  Play,
  UploadCloud,
  FileImage,
  ArrowUpRight,
  HardDrive,
  Clock,
  Sliders,
  Check,
  X,
  ShieldCheck,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { Button } from '../shared/ui/Button';
import { Badge } from '../shared/ui/Badge';
import { ConfirmModal } from '../shared/ui/ConfirmModal';

const POLL_INTERVAL_MS = 1500;

export const ImageSearchAdminPanel = () => {
  const [status, setStatus] = useState(null);
  const [loadingStatus, setLoadingStatus] = useState(true);
  const [reindexState, setReindexState] = useState(null);
  const [isReindexing, setIsReindexing] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [feedback, setFeedback] = useState(null);

  // Playground state
  const [testFile, setTestFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState(null);
  const [testError, setTestError] = useState(null);
  const fileInputRef = useRef(null);

  const pollTimerRef = useRef(null);

  const fetchStatus = useCallback(async () => {
    try {
      const res = await apiClient.get(ENDPOINTS.IMAGE_SEARCH.STATUS);
      const data = res?.data ?? res;
      setStatus(data);
      if (data?.reindex) {
        setReindexState(data.reindex);
        setIsReindexing(data.reindex.status === 'IN_PROGRESS');
      }
    } catch (err) {
      console.warn('Khong the lay trang thai Image Search:', err);
      setStatus({ ready: false, total_vectors: 0, error: err.message });
    } finally {
      setLoadingStatus(false);
    }
  }, []);

  const fetchReindexProgress = useCallback(async () => {
    try {
      const res = await apiClient.get(ENDPOINTS.IMAGE_SEARCH.REINDEX_STATUS);
      const data = res?.data ?? res;
      setReindexState(data);
      if (data.status === 'IN_PROGRESS') {
        setIsReindexing(true);
      } else {
        setIsReindexing(false);
        if (pollTimerRef.current) {
          clearInterval(pollTimerRef.current);
          pollTimerRef.current = null;
        }
        fetchStatus();
      }
    } catch (err) {
      console.warn('Loi cap nhat tien trinh reindex:', err);
    }
  }, [fetchStatus]);

  useEffect(() => {
    fetchStatus();
    return () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
      }
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [fetchStatus]);

  useEffect(() => {
    if (isReindexing && !pollTimerRef.current) {
      pollTimerRef.current = setInterval(fetchReindexProgress, POLL_INTERVAL_MS);
    }
    return () => {
      if (!isReindexing && pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [isReindexing, fetchReindexProgress]);

  const handleStartReindex = async () => {
    setConfirmOpen(false);
    setIsReindexing(true);
    setFeedback(null);
    try {
      const res = await apiClient.post(ENDPOINTS.IMAGE_SEARCH.REINDEX);
      const data = res?.data ?? res;
      setFeedback({
        type: 'success',
        message: 'Đã gửi yêu cầu khởi tạo chỉ mục hình ảnh. Quá trình đang thực thi nền.',
      });
      fetchReindexProgress();
      if (!pollTimerRef.current) {
        pollTimerRef.current = setInterval(fetchReindexProgress, POLL_INTERVAL_MS);
      }
    } catch (err) {
      setIsReindexing(false);
      setFeedback({
        type: 'error',
        message: err.message || 'Không thể bắt đầu tiến trình đồng bộ vector.',
      });
    }
  };

  const handleFileSelect = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    setTestFile(file);
    setPreviewUrl(URL.createObjectURL(file));
    setTestResult(null);
    setTestError(null);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    const file = e.dataTransfer.files?.[0];
    if (!file) return;
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    setTestFile(file);
    setPreviewUrl(URL.createObjectURL(file));
    setTestResult(null);
    setTestError(null);
  };

  const handleExecuteTest = async () => {
    if (!testFile) return;
    setIsTesting(true);
    setTestError(null);
    setTestResult(null);
    try {
      const formData = new FormData();
      formData.append('image', testFile);
      const res = await apiClient.post(ENDPOINTS.IMAGE_SEARCH.TEST_SEARCH, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const data = res?.data ?? res;
      setTestResult(data);
    } catch (err) {
      setTestError(err.message || 'Không thể đối soát hình ảnh.');
    } finally {
      setIsTesting(false);
    }
  };

  const total = reindexState?.total_images || 0;
  const processed = reindexState?.processed_images || 0;
  const percent = reindexState?.progress_percent || 0;
  const elapsed = reindexState?.elapsed_seconds || 0;
  const failed = reindexState?.failed_images || 0;
  const isHealthy = status?.ready === true;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* SECTION HEADER */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: 'var(--color-text-primary, #111827)', letterSpacing: '-0.02em' }}>
            Chỉ mục tìm kiếm bằng hình ảnh
          </h2>
          <p style={{ margin: '4px 0 0 0', fontSize: 13.5, color: 'var(--color-text-secondary, #6b7280)', lineHeight: 1.5 }}>
            Quản lý kho vector đặc trưng hình ảnh Qdrant, đồng bộ catalog từ kho lưu trữ và kiểm tra độ chính xác mô hình thị giác.
          </p>
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={fetchStatus}
            disabled={loadingStatus}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}
          >
            <RefreshCw size={14} className={loadingStatus ? 'animate-spin' : ''} />
            Làm mới trạng thái
          </Button>
        </div>
      </div>

      {feedback && (
        <div
          style={{
            padding: '12px 16px',
            borderRadius: 8,
            fontSize: 13.5,
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            background: feedback.type === 'error' ? 'var(--color-danger-50, #fef2f2)' : 'var(--color-success-50, #f0fdf4)',
            color: feedback.type === 'error' ? 'var(--color-danger-700, #b91c1c)' : 'var(--color-success-700, #15803d)',
            border: `1px solid ${feedback.type === 'error' ? 'var(--color-danger-200, #fecaca)' : 'var(--color-success-200, #bbf7d0)'}`,
          }}
        >
          {feedback.type === 'error' ? <AlertCircle size={16} /> : <CheckCircle2 size={16} />}
          <span>{feedback.message}</span>
          <button
            type="button"
            onClick={() => setFeedback(null)}
            style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer', color: 'inherit' }}
          >
            <X size={14} />
          </button>
        </div>
      )}

      {/* 1. METRICS CARDS */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
        <div style={{ padding: '16px 20px', borderRadius: 10, border: '1px solid var(--color-border, #e5e7eb)', background: 'var(--color-surface, #ffffff)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: 'var(--color-text-secondary, #6b7280)', marginBottom: 8 }}>
            <span style={{ fontSize: 12.5, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Vector đã lập chỉ mục</span>
            <Layers size={16} />
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, color: 'var(--color-text-primary, #111827)' }}>
            {status?.total_vectors != null ? Number(status.total_vectors).toLocaleString('vi-VN') : '—'}
          </div>
          <div style={{ fontSize: 12, color: 'var(--color-text-muted, #9ca3af)', marginTop: 4 }}>
            Điểm vector trong collection product_images
          </div>
        </div>

        <div style={{ padding: '16px 20px', borderRadius: 10, border: '1px solid var(--color-border, #e5e7eb)', background: 'var(--color-surface, #ffffff)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: 'var(--color-text-secondary, #6b7280)', marginBottom: 8 }}>
            <span style={{ fontSize: 12.5, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Mô hình trích xuất</span>
            <Camera size={16} />
          </div>
          <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--color-text-primary, #111827)', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
            CLIP ViT-B/32
          </div>
          <div style={{ fontSize: 12, color: 'var(--color-text-muted, #9ca3af)', marginTop: 4 }}>
            Độ dài vector: 512 chiều (L2 Cosine)
          </div>
        </div>

        <div style={{ padding: '16px 20px', borderRadius: 10, border: '1px solid var(--color-border, #e5e7eb)', background: 'var(--color-surface, #ffffff)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: 'var(--color-text-secondary, #6b7280)', marginBottom: 8 }}>
            <span style={{ fontSize: 12.5, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Lưu trữ Vector</span>
            <Database size={16} />
          </div>
          <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--color-text-primary, #111827)' }}>
            Qdrant Engine Độc Lập
          </div>
          <div style={{ fontSize: 12, color: 'var(--color-text-muted, #9ca3af)', marginTop: 4 }}>
            Cổng 8001 · Tách biệt hoàn toàn Chatbot
          </div>
        </div>

        <div style={{ padding: '16px 20px', borderRadius: 10, border: '1px solid var(--color-border, #e5e7eb)', background: 'var(--color-surface, #ffffff)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: 'var(--color-text-secondary, #6b7280)', marginBottom: 8 }}>
            <span style={{ fontSize: 12.5, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Trạng thái dịch vụ</span>
            <ShieldCheck size={16} />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: isHealthy ? '#22c55e' : '#ef4444' }} />
            <span style={{ fontSize: 15, fontWeight: 700, color: isHealthy ? '#15803d' : '#b91c1c' }}>
              {isHealthy ? 'Sẵn sàng hoạt động' : 'Chưa kết nối'}
            </span>
          </div>
          <div style={{ fontSize: 12, color: 'var(--color-text-muted, #9ca3af)', marginTop: 4 }}>
            {isHealthy ? 'Chỉ mục vector phản hồi bình thường' : status?.error || 'Dịch vụ chưa phản hồi'}
          </div>
        </div>
      </div>

      {/* 2. BULK RE-INDEX ACTION SECTION */}
      <div style={{ borderRadius: 12, border: '1px solid var(--color-border, #e5e7eb)', background: 'var(--color-surface, #ffffff)', padding: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
          <div style={{ maxWidth: 620 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
              <HardDrive size={18} style={{ color: 'var(--color-primary-600, #4f46e5)' }} />
              <h3 style={{ fontSize: 16, fontWeight: 700, margin: 0, color: 'var(--color-text-primary, #111827)' }}>
                Đồng bộ toàn bộ chỉ mục hình ảnh
              </h3>
            </div>
            <p style={{ margin: 0, fontSize: 13, color: 'var(--color-text-secondary, #6b7280)', lineHeight: 1.5 }}>
              Quét toàn bộ đường dẫn ảnh sản phẩm từ cơ sở dữ liệu MySQL và tệp vật lý trong volume lưu trữ của Docker,
              tính toán vector đặc trưng bằng mô hình CLIP và nạp vào Qdrant. Quá trình chạy ngầm, không làm gián đoạn
              các giao dịch hiện tại của khách hàng.
            </p>
          </div>

          <div style={{ display: 'flex', gap: 10 }}>
            <Button
              type="button"
              variant="primary"
              onClick={() => setConfirmOpen(true)}
              disabled={isReindexing || !isHealthy}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}
            >
              {isReindexing ? (
                <>
                  <RefreshCw size={15} className="animate-spin" />
                  Đang đồng bộ dữ liệu...
                </>
              ) : (
                <>
                  <Play size={15} />
                  Bắt đầu đồng bộ toàn bộ
                </>
              )}
            </Button>
          </div>
        </div>

        {/* PROGRESS DISPLAY */}
        {(isReindexing || reindexState?.status === 'COMPLETED' || reindexState?.status === 'FAILED') && (
          <div
            style={{
              marginTop: 20,
              padding: 16,
              borderRadius: 8,
              background: 'var(--color-surface-hover, #f9fafb)',
              border: '1px solid var(--color-border, #e5e7eb)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8, fontSize: 13 }}>
              <span style={{ fontWeight: 600, color: 'var(--color-text-primary, #111827)' }}>
                {isReindexing ? 'Tiến trình trích xuất vector' : reindexState?.status === 'COMPLETED' ? 'Đã hoàn tất đồng bộ' : 'Tiến trình bị gián đoạn'}
              </span>
              <span style={{ fontWeight: 700, color: 'var(--color-primary-600, #4f46e5)' }}>
                {percent}%
              </span>
            </div>

            {/* PROGRESS BAR */}
            <div
              style={{
                width: '100%',
                height: 8,
                borderRadius: 4,
                background: 'var(--color-border, #e5e7eb)',
                overflow: 'hidden',
                position: 'relative',
              }}
            >
              <div
                style={{
                  height: '100%',
                  width: `${percent}%`,
                  background: reindexState?.status === 'FAILED' ? 'var(--color-danger-500, #ef4444)' : 'var(--color-primary-600, #4f46e5)',
                  transition: 'width 0.4s ease',
                }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 10, fontSize: 12, color: 'var(--color-text-secondary, #6b7280)' }}>
              <div style={{ display: 'flex', gap: 16 }}>
                <span>Đã xử lý: <strong>{processed}</strong> / <strong>{total}</strong> ảnh</span>
                <span>Lỗi đọc tệp: <strong style={{ color: failed > 0 ? '#ef4444' : 'inherit' }}>{failed}</strong></span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <Clock size={13} />
                <span>Thời gian: {elapsed}s</span>
              </div>
            </div>

            {reindexState?.error_message && (
              <div style={{ marginTop: 8, fontSize: 12, color: 'var(--color-danger-700, #b91c1c)' }}>
                Ghi chú sự cố: {reindexState.error_message}
              </div>
            )}
          </div>
        )}
      </div>

      {/* 3. VISUAL SEARCH PLAYGROUND / PREVIEW */}
      <div style={{ borderRadius: 12, border: '1px solid var(--color-border, #e5e7eb)', background: 'var(--color-surface, #ffffff)', padding: 24 }}>
        <div style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
            <Sliders size={18} style={{ color: 'var(--color-primary-600, #4f46e5)' }} />
            <h3 style={{ fontSize: 16, fontWeight: 700, margin: 0, color: 'var(--color-text-primary, #111827)' }}>
              Kiểm tra độ tương đồng trực quan
            </h3>
          </div>
          <p style={{ margin: 0, fontSize: 13, color: 'var(--color-text-secondary, #6b7280)', lineHeight: 1.5 }}>
            Tải lên hình ảnh thử nghiệm bất kỳ để đối soát kết quả tìm kiếm sản phẩm và điểm tương đồng Cosine trả về từ Qdrant.
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: previewUrl ? '280px 1fr' : '1fr', gap: 20 }}>
          {/* UPLOAD / DROP ZONE */}
          <div>
            <div
              onDragOver={(e) => e.preventDefault()}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              style={{
                border: '2px dashed var(--color-border, #d1d5db)',
                borderRadius: 10,
                padding: 24,
                textAlign: 'center',
                cursor: 'pointer',
                background: 'var(--color-surface-hover, #fafafa)',
                transition: 'border-color 0.2s',
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/webp,image/avif"
                style={{ display: 'none' }}
                onChange={handleFileSelect}
              />
              <UploadCloud size={28} style={{ margin: '0 auto 8px', color: 'var(--color-text-muted, #9ca3af)' }} />
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-primary, #111827)' }}>
                Kéo thả hoặc chọn tệp ảnh
              </div>
              <div style={{ fontSize: 11.5, color: 'var(--color-text-muted, #9ca3af)', marginTop: 4 }}>
                Hỗ trợ JPEG, PNG, WebP (Tối đa 5 MB)
              </div>
            </div>

            {previewUrl && (
              <div style={{ marginTop: 12, borderRadius: 8, overflow: 'hidden', border: '1px solid var(--color-border, #e5e7eb)' }}>
                <img
                  src={previewUrl}
                  alt="Ảnh kiểm tra"
                  style={{ width: '100%', height: 180, objectFit: 'contain', background: '#00000008' }}
                />
                <div style={{ padding: '8px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#ffffff' }}>
                  <span style={{ fontSize: 12, color: 'var(--color-text-secondary, #6b7280)', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap', maxWidth: 160 }}>
                    {testFile?.name}
                  </span>
                  <Button
                    type="button"
                    variant="primary"
                    size="sm"
                    onClick={handleExecuteTest}
                    disabled={isTesting || !isHealthy}
                  >
                    {isTesting ? <RefreshCw size={13} className="animate-spin" /> : 'Đối soát'}
                  </Button>
                </div>
              </div>
            )}
          </div>

          {/* RESULTS LIST */}
          <div>
            {isTesting && (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200, color: 'var(--color-text-secondary, #6b7280)', gap: 8 }}>
                <RefreshCw size={18} className="animate-spin" />
                <span style={{ fontSize: 13 }}>Đang trích xuất vector đặc trưng và đối soát...</span>
              </div>
            )}

            {testError && (
              <div style={{ padding: 14, borderRadius: 8, background: '#fef2f2', color: '#b91c1c', border: '1px solid #fecaca', fontSize: 13 }}>
                {testError}
              </div>
            )}

            {!isTesting && testResult && (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-primary, #111827)' }}>
                    Ứng viên tương đồng tìm thấy ({testResult.items?.length || 0})
                  </span>
                  <Badge variant="outline" size="sm">
                    {testResult.index_version || 'qdrant'}
                  </Badge>
                </div>

                {testResult.items?.length === 0 ? (
                  <div style={{ padding: 24, textAlign: 'center', color: 'var(--color-text-muted, #9ca3af)', fontSize: 13 }}>
                    Không có sản phẩm nào đạt ngưỡng tương đồng phù hợp.
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 380, overflowY: 'auto' }}>
                    {testResult.items.map((item, idx) => {
                      const score = Math.round((item.similarity || 0) * 100);
                      return (
                        <div
                          key={`${item.product_id}-${item.image_id}-${idx}`}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '10px 14px',
                            borderRadius: 8,
                            border: '1px solid var(--color-border, #e5e7eb)',
                            background: '#ffffff',
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
                            <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--color-text-muted, #9ca3af)', width: 18 }}>
                              #{idx + 1}
                            </span>
                            <div style={{ minWidth: 0 }}>
                              <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--color-text-primary, #111827)', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                                {item.product_name || `Sản phẩm ID: ${item.product_id}`}
                              </div>
                              <div style={{ fontSize: 11.5, color: 'var(--color-text-muted, #9ca3af)' }}>
                                Product #{item.product_id} · Image #{item.image_id}
                              </div>
                            </div>
                          </div>

                          <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexShrink: 0 }}>
                            <div style={{ textAlign: 'right' }}>
                              <div style={{ fontSize: 13, fontWeight: 700, color: score > 80 ? '#15803d' : '#4f46e5' }}>
                                {score}%
                              </div>
                              <div style={{ fontSize: 10.5, color: 'var(--color-text-muted, #9ca3af)' }}>
                                Tương đồng Cosine
                              </div>
                            </div>
                            <a
                              href={`/product/${item.product_id}`}
                              target="_blank"
                              rel="noreferrer"
                              style={{ color: 'var(--color-text-muted, #9ca3af)', padding: 4 }}
                              title="Xem trang sản phẩm"
                            >
                              <ArrowUpRight size={16} />
                            </a>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}

            {!isTesting && !testResult && !previewUrl && (
              <div
                style={{
                  height: 160,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'var(--color-text-muted, #9ca3af)',
                  fontSize: 13,
                  border: '1px dashed var(--color-border, #e5e7eb)',
                  borderRadius: 8,
                }}
              >
                Chưa có ảnh đối soát. Hãy kéo thả hoặc chọn tệp ảnh ở khung bên trái.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* CONFIRMATION MODAL */}
      <ConfirmModal
        isOpen={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={handleStartReindex}
        title="Xác nhận đồng bộ toàn bộ chỉ mục hình ảnh"
        message="Hệ thống sẽ quét lại toàn bộ kho ảnh catalog trong cơ sở dữ liệu và nạp lại vào kho vector Qdrant."
        detail="Quá trình này sử dụng mô hình CLIP ViT-B/32 để trích xuất đặc trưng thị giác. Các sản phẩm mới hoặc đã cập nhật ảnh sẽ được lập chỉ mục đầy đủ."
        confirmText="Xác nhận thực thi"
        cancelText="Đóng"
        isPermanent={false}
      />
    </div>
  );
};

