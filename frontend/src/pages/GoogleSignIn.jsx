import { useEffect, useRef, useState } from 'react';

const clientId = import.meta.env.VITE_GOOGLE_OAUTH_CLIENT_ID?.trim();
let googleScript;

function loadGoogleSignIn() {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id);
  if (!googleScript) {
    googleScript = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client?hl=vi';
      script.async = true;
      script.onload = () => {
        const google = window.google?.accounts?.id;
        if (google) resolve(google);
        else reject(new Error('Google Identity Services unavailable'));
      };
      script.onerror = () => {
        script.remove();
        reject(new Error('Google Identity Services unavailable'));
      };
      document.head.append(script);
    }).catch((error) => {
      googleScript = undefined;
      throw error;
    });
  }
  return googleScript;
}

export function GoogleSignIn({ signIn, busy, setBusy }) {
  const buttonRef = useRef(null);
  const submitting = useRef(false);
  const [credential, setCredential] = useState(null);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const submit = async (googleCredential, confirmationPassword) => {
    if (busy || submitting.current || !googleCredential) return;
    submitting.current = true;
    setBusy(true);
    setError('');
    try {
      await signIn(googleCredential, confirmationPassword);
      setCredential(null);
    } catch (err) {
      const data = err.response?.data;
      const code = data?.errorCode;
      if (code === 'AUTH_GOOGLE_LINK_REQUIRED') {
        setCredential(googleCredential);
      } else if (code === 'AUTH_GOOGLE_INVALID') {
        setCredential(null);
        setError(data.message);
      } else {
        setError(data?.message || 'Đăng nhập Google thất bại. Vui lòng thử lại.');
      }
    } finally {
      setPassword('');
      submitting.current = false;
      setBusy(false);
    }
  };
  const submitRef = useRef(submit);
  submitRef.current = submit;

  useEffect(() => {
    if (!clientId || credential) return;
    const button = buttonRef.current;
    let mounted = true;
    loadGoogleSignIn().then((google) => {
      if (!mounted || !button) return;
      google.initialize({
        client_id: clientId,
        callback: ({ credential: googleCredential }) => {
          if (mounted) void submitRef.current(googleCredential);
        },
      });
      google.renderButton(button, { theme: 'outline', size: 'large', text: 'continue_with', locale: 'vi' });
    }).catch(() => {
      if (mounted) setError('Không tải được nút đăng nhập Google. Vui lòng thử tải lại trang.');
    });
    return () => {
      mounted = false;
      button?.replaceChildren();
    };
  }, [credential]);

  if (!clientId) return null;
  return (
    <section className="customer-google-sign-in" aria-label="Đăng nhập Google" aria-busy={busy}>
      {credential ? (
        <form onSubmit={(event) => { event.preventDefault(); void submit(credential, password); }}>
          <label htmlFor="google-link-password">Nhập mật khẩu BeautyShop để liên kết tài khoản Google.</label>
          <input
            id="google-link-password"
            type="password"
            autoComplete="current-password"
            maxLength={100}
            required
            disabled={busy}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <button type="submit" disabled={busy}>Xác nhận liên kết</button>
          <button type="button" disabled={busy} onClick={() => {
            setCredential(null);
            setPassword('');
            setError('');
          }}>Hủy</button>
        </form>
      ) : <div className="customer-google-sign-in__button" ref={buttonRef} inert={busy} />}
      {busy && submitting.current && <p role="status">Đang xác thực với Google…</p>}
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
