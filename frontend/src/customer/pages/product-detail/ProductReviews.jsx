import React, { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { CheckCircle2, MessageSquare, Star } from 'lucide-react';
import { apiClient } from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/endpoints';

const REVIEW_PAGE_SIZE = 10;
const ORDER_PAGE_SIZE = 100;
const initialForm = () => ({ orderId: '', rating: 5, title: '', content: '' });
const initialReviewPage = () => ({ rows: [], total: null, page: -1, hasMore: false, loading: true, error: '' });
const unwrap = (response) => response?.data ?? response;
const productVariantKey = (product) => (product.variants ?? []).map((variant) => Number(variant.id))
  .filter((id) => Number.isSafeInteger(id) && id > 0).sort((a, b) => a - b).join(',');

const readPage = (response, requestedPage, pageSize) => {
  const page = unwrap(response);
  const total = Number(page?.totalElements);
  if (!Array.isArray(page?.content) || page.totalElements == null || !Number.isSafeInteger(total) || total < 0) {
    throw new Error('Dữ liệu phân trang chưa hợp lệ.');
  }
  const currentPage = Number(page.page ?? page.number ?? requestedPage);
  const totalPages = Number(page.totalPages ?? Math.ceil(total / pageSize));
  if (currentPage !== requestedPage || !Number.isSafeInteger(totalPages) || totalPages < 0) {
    throw new Error('Dữ liệu phân trang chưa hợp lệ.');
  }
  return {
    rows: page.content,
    total,
    hasMore: page.last !== true && currentPage + 1 < totalPages,
  };
};

const formatDate = (value) => {
  if (!value) return '';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '';
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh',
  }).format(date);
};

const submissionError = (error) => {
  const status = error?.status ?? error?.response?.status;
  if (status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để gửi bình luận.';
  if (status === 403) return 'Bạn chưa có quyền gửi nội dung này. Vui lòng kiểm tra tài khoản.';
  const message = error?.response?.data?.message ?? error?.message ?? '';
  // Keep useful Vietnamese business errors, without exposing technical messages.
  if (/[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]/i.test(message)
    && !/Exception|SQL|java\.|stack trace|HTTP \d|[ÃÂ]/.test(message)) return message;
  return 'Chưa thể gửi nội dung. Vui lòng thử lại; thông tin bạn đã nhập vẫn được giữ.';
};

const RatingStars = ({ value, size = 16 }) => {
  const rating = Math.min(5, Math.max(0, Number(value) || 0));
  return (
    <span className="pd-stars" role="img" aria-label={`${rating.toLocaleString('vi-VN')} trên 5 sao`}>
      {[1, 2, 3, 4, 5].map((star) => (
        <span className="pd-star" key={star} style={{ position: 'relative', display: 'inline-flex' }}>
          <Star size={size} color="#C9C1B6" aria-hidden="true" />
          <span className="pd-star-fill" style={{ position: 'absolute', inset: 0, overflow: 'hidden', width: `${Math.min(1, Math.max(0, rating - star + 1)) * 100}%` }}>
            <Star size={size} fill="#C89C46" color="#C89C46" aria-hidden="true" />
          </span>
        </span>
      ))}
    </span>
  );
};

const ProductReviewsContent = ({ product, isAuthenticated, onNavigate }) => {
  const productId = product.id;
  const fieldId = useId();
  const [reviewPage, setReviewPage] = useState(initialReviewPage);
  const [eligibility, setEligibility] = useState(() => ({ phase: isAuthenticated ? 'loading' : 'guest', orders: [] }));
  const [eligibilityRetry, setEligibilityRetry] = useState(0);
  const [form, setForm] = useState(initialForm);
  const [submittedOrderIds, setSubmittedOrderIds] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState('');
  const [error, setError] = useState('');
  const reviewsRequest = useRef(null);
  const loadedPage = useRef(-1);
  const submissionRequest = useRef(null);
  const variantKey = productVariantKey(product);
  const availableOrders = useMemo(() => eligibility.phase === 'known'
    ? eligibility.orders.filter((order) => !submittedOrderIds.includes(Number(order.id)))
    : [], [eligibility, submittedOrderIds]);
  const canRate = availableOrders.length > 0;
  const selectedOrderId = availableOrders.some((order) => String(order.id) === form.orderId)
    ? form.orderId : String(availableOrders[0]?.id ?? '');
  const verifiedCountValue = Number(product.totalReviews ?? 0);
  const verifiedCount = Number.isSafeInteger(verifiedCountValue) && verifiedCountValue > 0 ? verifiedCountValue : 0;
  const ratingValue = Number(product.averageRating);
  const hasAverage = verifiedCount > 0 && Number.isFinite(ratingValue) && ratingValue >= 1 && ratingValue <= 5;

  const loadReviewPage = useCallback((page) => {
    if (reviewsRequest.current || (page > 0 && page <= loadedPage.current)) return;
    const controller = new AbortController();
    reviewsRequest.current = controller;
    return apiClient.get(`/api/v1/reviews?productId=${encodeURIComponent(productId)}&page=${page}&size=${REVIEW_PAGE_SIZE}&sort=createdAt,desc`, { signal: controller.signal }).then((response) => {
      if (controller.signal.aborted || reviewsRequest.current !== controller) return;
      const result = readPage(response, page, REVIEW_PAGE_SIZE);
      loadedPage.current = page;
      setReviewPage((previous) => {
        const rows = page === 0 ? result.rows : [...previous.rows, ...result.rows];
        const distinct = [...new Map(rows.map((review) => [String(review.id), review])).values()];
        return { rows: distinct, total: result.total, page, hasMore: result.hasMore, loading: false, error: '' };
      });
    }).catch(() => {
      if (!controller.signal.aborted && reviewsRequest.current === controller) {
        setReviewPage((previous) => ({ ...previous, loading: false, error: page === 0
          ? 'Chưa thể tải đánh giá và bình luận. Vui lòng thử lại.'
          : 'Chưa thể tải thêm bình luận. Các nội dung đã tải vẫn được giữ.' }));
      }
    }).finally(() => {
      if (reviewsRequest.current === controller) reviewsRequest.current = null;
    });
  }, [productId]);

  const requestReviewPage = (page) => {
    if (reviewsRequest.current || (page > 0 && page <= loadedPage.current)) return;
    setReviewPage((previous) => ({ ...previous, loading: true, error: '' }));
    loadReviewPage(page);
  };

  useEffect(() => {
    loadReviewPage(0);
    return () => {
      reviewsRequest.current?.abort();
      reviewsRequest.current = null;
    };
  }, [loadReviewPage]);

  useEffect(() => {
    const controller = new AbortController();
    if (!isAuthenticated) return () => controller.abort();

    const findOrders = async () => {
      const variantIds = new Set(variantKey ? variantKey.split(',').map(Number) : []);
      const orders = [];
      let page = 0;
      let hasMore = true;
      try {
        while (hasMore && !controller.signal.aborted) {
          const response = await apiClient.get(`${ENDPOINTS.ORDERS.MY_ORDERS}?page=${page}&size=${ORDER_PAGE_SIZE}&sort=createdAt,desc`, { signal: controller.signal });
          if (controller.signal.aborted) return;
          const result = readPage(response, page, ORDER_PAGE_SIZE);
          orders.push(...result.rows.filter((order) => order.status === 'DELIVERED'
            && (order.items ?? []).some((item) => variantIds.has(Number(item.variantId)))));
          hasMore = result.hasMore;
          page += 1;
        }
        if (controller.signal.aborted) return;
        const distinct = [...new Map(orders.map((order) => [String(order.id), order])).values()];
        setEligibility({ phase: 'known', orders: distinct });
      } catch {
        if (!controller.signal.aborted) setEligibility({ phase: 'unknown', orders: [] });
      }
    };
    findOrders();
    return () => controller.abort();
  }, [isAuthenticated, productId, variantKey, eligibilityRetry]);

  useEffect(() => () => {
    submissionRequest.current?.abort();
    submissionRequest.current = null;
  }, []);

  const submitReview = async (event) => {
    event.preventDefault();
    if (!isAuthenticated || submissionRequest.current) return;
    setError('');
    setSuccess('');
    const content = form.content.trim();
    const title = form.title.trim();
    const rating = Number(form.rating);
    const orderId = Number(selectedOrderId);
    if (!content || content.length > 2000) {
      setError('Vui lòng nhập nội dung bình luận, tối đa 2.000 ký tự.');
      return;
    }
    if (title.length > 150) {
      setError('Tiêu đề bình luận không được quá 150 ký tự.');
      return;
    }
    if (canRate && !availableOrders.some((order) => Number(order.id) === orderId)) {
      setError('Vui lòng chọn đơn hàng đã giao phù hợp với sản phẩm.');
      return;
    }
    if (canRate && (!Number.isInteger(rating) || rating < 1 || rating > 5)) {
      setError('Vui lòng chọn số sao từ 1 đến 5.');
      return;
    }

    const controller = new AbortController();
    submissionRequest.current = controller;
    setSubmitting(true);
    try {
      const payload = { productId: Number(productId), title: title || null, content };
      if (canRate) Object.assign(payload, { orderId, rating });
      const response = unwrap(await apiClient.post('/api/v1/reviews', payload, { signal: controller.signal }));
      if (controller.signal.aborted || submissionRequest.current !== controller) return;
      if (!response?.id || Number(response.productId) !== Number(productId)) throw new Error('Chưa xác nhận được kết quả gửi.');
      if (canRate) setSubmittedOrderIds((ids) => [...new Set([...ids, orderId])]);
      const nextOrder = canRate ? availableOrders.find((order) => Number(order.id) !== orderId) : null;
      setForm({ orderId: nextOrder?.id ? String(nextOrder.id) : '', rating: 5, title: '', content: '' });
      setSuccess(canRate ? 'Đánh giá đã gửi và đang chờ duyệt.' : 'Bình luận đã gửi và đang chờ duyệt.');
    } catch (requestError) {
      if (!controller.signal.aborted && submissionRequest.current === controller) setError(submissionError(requestError));
    } finally {
      if (submissionRequest.current === controller) {
        submissionRequest.current = null;
        setSubmitting(false);
      }
    }
  };

  return (
    <section id="product-reviews" className="product-content-section pd-reviews" tabIndex={-1} aria-labelledby={`${fieldId}-heading`}>
      <header className="product-section-heading">
        <span className="pd-eyebrow">TRẢI NGHIỆM KHÁCH HÀNG</span>
        <h2 id={`${fieldId}-heading`}>Đánh giá và bình luận</h2>
      </header>
      <div className="product-section-body">
        <div className="pd-review-summary">
          <div className="pd-rating-summary">
            <strong className="pd-rating-value">{hasAverage ? `${ratingValue.toLocaleString('vi-VN', { maximumFractionDigits: 2 })} / 5` : 'Chưa có điểm chấm sao'}</strong>
            {hasAverage && <RatingStars value={ratingValue} size={20} />}
            <span>{verifiedCount.toLocaleString('vi-VN')} lượt chấm sao từ đơn đã mua</span>
          </div>
          <div className="pd-review-total">
            <MessageSquare size={22} aria-hidden="true" />
            <span>{reviewPage.total === null ? reviewPage.error ? 'Chưa xác minh tổng bình luận công khai' : 'Đang xác minh tổng bình luận công khai' : `${reviewPage.total.toLocaleString('vi-VN')} đánh giá và bình luận công khai`}</span>
            <p>Số điểm trung bình chỉ tính các đánh giá mua hàng đã xác thực và được duyệt.</p>
          </div>
        </div>

        <div className="pd-review-compose">
          <h3>Chia sẻ trải nghiệm của bạn</h3>
          <p>Nội dung sẽ hiển thị sau khi BeautyShop duyệt.</p>
          {success && <div className="pd-review-feedback is-success" role="status">{success}</div>}
          {error && <div className="pd-review-feedback is-error" role="alert">{error}</div>}
          {!isAuthenticated ? (
            <div className="pd-review-footer">
              <p>Đăng nhập để bình luận sản phẩm. Khách có đơn đã giao phù hợp có thể chấm sao.</p>
              <button type="button" className="btn-luxury-primary" onClick={() => onNavigate('login')}>Đăng nhập để bình luận</button>
            </div>
          ) : (
            <form className="pd-review-form" onSubmit={submitReview} aria-busy={submitting}>
              <fieldset disabled={submitting} className="pd-review-fields">
                {canRate ? (
                  <>
                    <div className="pd-review-form-row">
                      <label className="pd-review-field" htmlFor={`${fieldId}-order`}>Đơn hàng đã giao
                        <select id={`${fieldId}-order`} value={selectedOrderId} onChange={(event) => setForm((previous) => ({ ...previous, orderId: event.target.value }))}>
                          {availableOrders.map((order) => <option key={order.id} value={order.id}>{order.orderNumber || `Đơn #${order.id}`}{order.createdAt ? ` · ${formatDate(order.createdAt)}` : ''}</option>)}
                        </select>
                      </label>
                      <fieldset className="pd-review-rating-options">
                        <legend>Số sao</legend>
                        {[1, 2, 3, 4, 5].map((star) => (
                          <label className="pd-review-radio" key={star}>
                            <input type="radio" name={`${fieldId}-rating`} value={star} checked={Number(form.rating) === star} onChange={() => setForm((previous) => ({ ...previous, rating: star }))} aria-label={`${star} sao`} />
                            <Star size={22} fill={star <= Number(form.rating) ? '#C89C46' : 'none'} color="#C89C46" aria-hidden="true" />
                          </label>
                        ))}
                      </fieldset>
                    </div>
                    <p className="pd-review-note">Mỗi sản phẩm trong một đơn hàng được chấm sao một lần. Hệ thống kiểm tra lại quyền đánh giá và nội dung đã gửi khi bạn xác nhận.</p>
                  </>
                ) : eligibility.phase === 'loading' ? (
                  <p className="pd-review-note" role="status">Đang xác minh quyền chấm sao. Bạn vẫn có thể gửi bình luận không kèm số sao.</p>
                ) : eligibility.phase === 'unknown' ? (
                  <div className="pd-review-note">
                    <strong>Chưa xác minh quyền chấm sao</strong>
                    <p>Chưa thể tải đầy đủ đơn hàng. Bạn vẫn có thể gửi bình luận không kèm số sao.</p>
                    <button type="button" className="pd-text-button" onClick={() => { setEligibility({ phase: 'loading', orders: [] }); setEligibilityRetry((value) => value + 1); }}>Kiểm tra lại quyền chấm sao</button>
                  </div>
                ) : (
                  <p className="pd-review-note">{submittedOrderIds.length > 0
                    ? 'Bạn đã gửi đánh giá cho các đơn phù hợp trong phiên này. Bạn vẫn có thể gửi bình luận không kèm số sao.'
                    : 'Chưa tìm thấy đơn đã giao phù hợp trong dữ liệu hiện có. Bạn có thể gửi bình luận không kèm số sao.'}</p>
                )}
                <label className="pd-review-field" htmlFor={`${fieldId}-title`}>Tiêu đề (không bắt buộc)
                  <input id={`${fieldId}-title`} type="text" value={form.title} maxLength={150} onChange={(event) => setForm((previous) => ({ ...previous, title: event.target.value }))} placeholder="Ấn tượng của bạn về sản phẩm" />
                </label>
                <label className="pd-review-field" htmlFor={`${fieldId}-content`}>Nội dung bình luận
                  <textarea id={`${fieldId}-content`} value={form.content} required maxLength={2000} rows={4} onChange={(event) => setForm((previous) => ({ ...previous, content: event.target.value }))} placeholder="Chia sẻ trải nghiệm hoặc câu hỏi về sản phẩm" aria-describedby={`${fieldId}-length`} />
                </label>
                <div className="pd-review-footer">
                  <span id={`${fieldId}-length`}>{form.content.length.toLocaleString('vi-VN')}/2.000 ký tự</span>
                  <button type="submit" className="btn-luxury-primary" disabled={submitting}>{submitting ? 'Đang gửi…' : canRate ? 'Gửi đánh giá' : 'Gửi bình luận'}</button>
                </div>
              </fieldset>
            </form>
          )}
        </div>

        {reviewPage.rows.length > 0 && (
          <div id="product-review-list" className="product-review-list pd-review-list">
            {reviewPage.rows.map((review) => (
              <article key={review.id}>
                <div className="product-review-header">
                  <div>
                    <strong>{review.isVerifiedPurchase ? 'Người mua đã xác thực' : 'Khách hàng BeautyShop'}</strong>
                    <span>{review.isVerifiedPurchase && <CheckCircle2 size={12} aria-hidden="true" />}{review.isVerifiedPurchase ? 'Đã xác thực đơn hàng' : 'Bình luận'}</span>
                  </div>
                  {formatDate(review.createdAt) && <time dateTime={review.createdAt}>{formatDate(review.createdAt)}</time>}
                </div>
                {review.rating != null && Number(review.rating) >= 1 && Number(review.rating) <= 5 && <RatingStars value={review.rating} />}
                {review.title && <h4>{review.title}</h4>}
                <p>{review.content}</p>
                {review.adminReply && <div className="product-review-reply"><strong>BeautyShop phản hồi:</strong> {review.adminReply}</div>}
              </article>
            ))}
          </div>
        )}
        {reviewPage.loading && <p className="pd-review-note" role="status">{reviewPage.rows.length ? 'Đang tải thêm bình luận…' : 'Đang tải đánh giá và bình luận…'}</p>}
        {reviewPage.error && (
          <div className="pd-review-load-error">
            <p role="alert">{reviewPage.error}</p>
            <button type="button" className="pd-text-button" onClick={() => requestReviewPage(reviewPage.page < 0 ? 0 : reviewPage.page + 1)}>Thử lại</button>
          </div>
        )}
        {!reviewPage.loading && !reviewPage.error && reviewPage.total === 0 && <div className="pd-review-empty"><MessageSquare size={26} aria-hidden="true" /><p>Chưa có đánh giá hoặc bình luận công khai cho sản phẩm này.</p></div>}
        {reviewPage.hasMore && !reviewPage.error && <div className="pd-review-footer"><span>Đã hiển thị {reviewPage.rows.length.toLocaleString('vi-VN')}/{reviewPage.total?.toLocaleString('vi-VN')} nội dung công khai</span><button type="button" className="pd-text-button" disabled={reviewPage.loading} onClick={() => requestReviewPage(reviewPage.page + 1)}>Tải thêm bình luận</button></div>}
      </div>
    </section>
  );
};

export const ProductReviews = (props) => (
  <ProductReviewsContent key={`${props.product.id}:${Boolean(props.isAuthenticated)}:${productVariantKey(props.product)}`} {...props} />
);
