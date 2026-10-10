import React, { useState, useEffect } from 'react';
import {
  Check,
  X,
  MessageCircle,
  Star,
  RefreshCw,
  Trash2,
} from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { getReviewStatusBadge } from '../shared/utils/formatters';
import { Button } from '../shared/ui/Button';
import { Select } from '../shared/ui/Select';
import { Badge } from '../shared/ui/Badge';
import { DataTable } from '../shared/ui/DataTable';
import { Modal } from '../shared/ui/Modal';

export const ReviewsPage = () => {
  const [reviews, setReviews] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(false);

  // Reply Modal
  const [replyModalOpen, setReplyModalOpen] = useState(false);
  const [selectedReview, setSelectedReview] = useState(null);
  const [replyContent, setReplyContent] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const fetchReviews = async (p = 0) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', p);
      params.append('size', '15');
      if (statusFilter) params.append('status', statusFilter);
      const res = await apiClient.get(`${ENDPOINTS.REVIEWS.LIST}?${params.toString()}`);
      const pageData = res.data || res;
      setReviews(pageData.content || []);
      setPage(pageData.page ?? p);
      setTotalPages(pageData.totalPages ?? 1);
      setTotalElements(pageData.totalElements ?? 0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReviews(0);
  }, [statusFilter]);

  const handleModerate = async (id, status) => {
    try {
      await apiClient.put(ENDPOINTS.REVIEWS.MODERATE(id), { status });
      fetchReviews(page);
    } catch (err) {
      alert(err.message || 'Lỗi cập nhật trạng thái kiểm duyệt');
    }
  };

  const handleOpenReply = (review) => {
    setSelectedReview(review);
    setReplyContent(review.adminReply || '');
    setReplyModalOpen(true);
  };

  const handleReplySubmit = async (e) => {
    e.preventDefault();
    if (!selectedReview) return;
    setSubmitting(true);
    try {
      await apiClient.put(ENDPOINTS.REVIEWS.REPLY(selectedReview.id), {
        reply: replyContent.trim(),
      });
      setReplyModalOpen(false);
      fetchReviews(page);
    } catch (err) {
      alert(err.message || 'Lỗi gửi phản hồi cho khách');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteReview = async (id) => {
    if (!window.confirm(`Xóa nhận xét #${id}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.REVIEWS.DELETE(id));
      fetchReviews(page);
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa nhận xét');
    }
  };

  const columns = [
    {
      header: 'ID',
      accessor: 'id',
      width: '60px',
    },
    {
      header: 'Mỹ phẩm / Đơn hàng',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600 }}>{row.productName}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{row.orderId ? `Mã đơn: #${row.orderId}` : 'Không gắn đơn'}</div>
        </div>
      ),
    },
    {
      header: 'Khách hàng',
      accessor: (row) => row.userName || `User #${row.userId}`,
    },
    {
      header: 'Đánh giá',
      accessor: (row) => row.rating ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: '2px' }}>
          {[...Array(5)].map((_, i) => (
            <Star
              key={i}
              size={13}
              fill={i < row.rating ? '#F59E0B' : 'transparent'}
              color={i < row.rating ? '#F59E0B' : '#CBD5E1'}
            />
          ))}
          <span style={{ fontSize: '12px', fontWeight: 700, marginLeft: '4px' }}>
            {row.rating}/5
          </span>
        </div>
      ) : <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Bình luận</span>,
      width: '120px',
    },
    {
      header: 'Nội dung nhận xét & Phản hồi',
      accessor: (row) => (
        <div>
          <div style={{ fontSize: '13px', color: 'var(--text-main)' }}>{row.content}</div>
          {row.adminReply && (
            <div
              style={{
                marginTop: '6px',
                padding: '6px 10px',
                backgroundColor: 'var(--color-primary-50)',
                borderLeft: '2px solid var(--color-accent-600)',
                fontSize: '11px',
              }}
            >
              <strong>Phản hồi từ Shop:</strong> {row.adminReply}
            </div>
          )}
        </div>
      ),
    },
    {
      header: 'Trạng thái',
      accessor: (row) => {
        const badge = getReviewStatusBadge(row.status);
        return <Badge variant={badge.variant}>{badge.text}</Badge>;
      },
    },
    {
      header: 'Thao tác kiểm duyệt',
      align: 'right',
      render: (row) => (
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '4px' }}>
          {row.status !== 'APPROVED' && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleModerate(row.id, 'APPROVED')}
              icon={Check}
              title="Duyệt hiển thị"
            >
              Duyệt
            </Button>
          )}
          {row.status !== 'REJECTED' && (
            <Button
              variant="danger"
              size="sm"
              onClick={() => handleModerate(row.id, 'REJECTED')}
              icon={X}
              title="Từ chối nhận xét này"
            >
              Từ chối
            </Button>
          )}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => handleOpenReply(row)}
            icon={MessageCircle}
            title="Trả lời khách hàng"
          >
            Phản hồi
          </Button>
          <Button
            variant="danger"
            size="sm"
            onClick={() => handleDeleteReview(row.id)}
            icon={Trash2}
            title="Xóa nhận xét"
          />
        </div>
      ),
    },
  ];

  return (
    <div className="content-container">
      <div className="page-header">
        <div>
          <h1 className="page-title">Kiểm duyệt Đánh giá Khách hàng</h1>
          <p className="page-subtitle">
            Duyệt nhận xét thực tế từ người mua hàng, lọc từ ngữ phản cảm và trả lời khách hàng
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => fetchReviews(page)}
          loading={loading}
          icon={RefreshCw}
        >
          Làm mới
        </Button>
      </div>

      <div className="admin-review-filters">
        <label htmlFor="admin-review-status" className="form-label">Lọc theo trạng thái kiểm duyệt</label>
        <Select
          id="admin-review-status"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="admin-review-status-field"
        >
          <option value="">Tất cả nhận xét</option>
          <option value="PENDING">Chờ kiểm duyệt</option>
          <option value="APPROVED">Đã duyệt hiển thị</option>
          <option value="REJECTED">Đã từ chối</option>
          <option value="HIDDEN">Đã ẩn</option>
        </Select>
      </div>

      <DataTable
        columns={columns}
        data={reviews}
        loading={loading}
        emptyMessage="Không có đánh giá nào phù hợp."
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={(p) => fetchReviews(p)}
      />

      {/* Reply Modal */}
      <Modal
        isOpen={replyModalOpen}
        onClose={() => setReplyModalOpen(false)}
        title="Trả lời đánh giá khách hàng"
        footer={
          <>
            <Button variant="outline" onClick={() => setReplyModalOpen(false)}>
              Hủy
            </Button>
            <Button
              variant="primary"
              onClick={handleReplySubmit}
              loading={submitting}
              icon={MessageCircle}
            >
              Gửi phản hồi
            </Button>
          </>
        }
      >
        <form onSubmit={handleReplySubmit}>
          <div
            style={{
              padding: '10px 12px',
              backgroundColor: 'var(--color-primary-50)',
              border: '1px solid var(--border-subtle)',
              fontSize: '12px',
              marginBottom: '16px',
            }}
          >
            Đánh giá của khách: <em>"{selectedReview?.content}"</em>
          </div>

          <div className="form-group">
            <label className="form-label">Nội dung phản hồi chính thức từ Quản trị viên</label>
            <textarea
              className="form-textarea"
              rows={4}
              value={replyContent}
              onChange={(e) => setReplyContent(e.target.value)}
              placeholder="Cảm ơn bạn đã tin tưởng BeautyShop..."
              required
            />
          </div>
        </form>
      </Modal>
    </div>
  );
};
