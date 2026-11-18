import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, LockKeyhole, Mail } from 'lucide-react';
import { apiClient } from '../shared/api/client';

export function PasswordRecoveryForm({ reset, initialToken, onBack }) {
  const heading = useRef(null);
  const [token, setToken] = useState(initialToken);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [feedback, setFeedback] = useState(null);
  const [busy, setBusy] = useState(false);
  const resetComplete = reset && feedback?.success;
  const title = reset ? 'Đặt lại mật khẩu' : 'Khôi phục tài khoản';

  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
  }, []);

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setFeedback(null);
    try {
      const response = await apiClient.post(
        reset ? '/api/v1/auth/reset-password' : '/api/v1/auth/forgot-password',
        reset ? { token: token.trim(), newPassword: password } : { email: email.trim() },
      );
      setFeedback({
        success: true,
        message: response?.message || (reset
          ? 'Đã đặt lại mật khẩu. Vui lòng đăng nhập lại.'
          : 'Yêu cầu đã được tiếp nhận. Vui lòng kiểm tra email.'),
      });
      if (reset) {
        setToken('');
        setPassword('');
      }
    } catch (error) {
      setFeedback({
        success: false,
        message: error?.response?.data?.message || error?.message || 'Chưa thể xử lý yêu cầu. Vui lòng thử lại.',
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="auth-slide-pane active auth-recovery" aria-labelledby="auth-recovery-title">
      <div className="auth-recovery-icon" aria-hidden="true">
        {reset ? <LockKeyhole size={18} /> : <Mail size={18} />}
      </div>
      <h1 ref={heading} tabIndex={-1} id="auth-recovery-title">{title}</h1>
      {!resetComplete && (
        <>
          <p className="auth-recovery-description">
            {reset ? 'Nhập mã khôi phục và mật khẩu mới cho tài khoản.'
              : 'Nhập email đã đăng ký để nhận hướng dẫn đặt lại mật khẩu.'}
          </p>
          <form onSubmit={submit}>
            {reset ? (
              <>
                <label>
                  Mã khôi phục
                  <input required autoComplete="off" value={token} onChange={(event) => setToken(event.target.value)} />
                </label>
                <label>
                  Mật khẩu mới
                  <input required type="password" minLength={8} maxLength={128} autoComplete="new-password"
                    value={password} onChange={(event) => setPassword(event.target.value)} />
                </label>
              </>
            ) : (
              <label>
                Email tài khoản
                <input required type="email" autoComplete="email" placeholder="Nhập email đã đăng ký"
                  value={email} onChange={(event) => setEmail(event.target.value)} />
              </label>
            )}
            <button type="submit" className="auth-recovery-submit" disabled={busy}>
              {busy ? 'Đang xử lý…' : reset ? title : 'Gửi hướng dẫn qua email'}
              <ArrowRight size={14} aria-hidden="true" />
            </button>
          </form>
        </>
      )}
      {feedback && (
        <p className={feedback.success ? 'auth-recovery-feedback' : 'auth-recovery-feedback is-error'}
          role={feedback.success ? 'status' : 'alert'}>{feedback.message}</p>
      )}
      <div className="auth-recovery-actions">
        <button type="button" onClick={onBack}><ArrowLeft size={14} aria-hidden="true" /> Về đăng nhập</button>
        {!resetComplete && (
          <a href={reset ? '#/recover-password' : '#/reset-password'}>
            {reset ? 'Gửi lại email khôi phục' : 'Tôi đã có mã khôi phục'}
          </a>
        )}
      </div>
    </section>
  );
}
