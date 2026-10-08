import React, { useState } from 'react';
import {
  ShieldCheck,
  Lock,
  User,
  AlertCircle,
  Eye,
  EyeOff,
  Sparkles,
  ArrowRight,
  CheckCircle2,
  ArrowLeft,
  Mail,
  Phone,
  Loader2,
  Gem,
  ArrowUpRight
} from 'lucide-react';
import { useAuth } from '../app/providers/AuthProvider';
import { Button } from '../shared/ui/Button';

export const LoginPage = () => {
  const { login, register } = useAuth();
  const route = window.location.hash.replace(/^#\/?/, '');
  const isAdminRoute = route.startsWith('admin') ||
    ['dashboard', 'inventory', 'procurement', 'crm', 'operations', 'vouchers', 'reviews', 'users'].includes(route);
  const [authMode, setAuthMode] = useState('login'); // 'login' | 'register'

  // Login State
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  // Register State
  const [regFullName, setRegFullName] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regPhone, setRegPhone] = useState('');
  const [regUsername, setRegUsername] = useState('');
  const [regPassword, setRegPassword] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (authMode === 'register' && !isAdminRoute) {
      if (!regFullName.trim() || !regEmail.trim() || !regUsername.trim() || !regPassword.trim()) {
        setError('Vui lòng điền đầy đủ các thông tin bắt buộc.');
        return;
      }
      if (regPassword.length < 6) {
        setError('Mật khẩu bảo mật phải có tối thiểu 6 ký tự.');
        return;
      }

      setLoading(true);
      try {
        await register({
          fullName: regFullName.trim(),
          email: regEmail.trim(),
          phone: regPhone.trim() || undefined,
          username: regUsername.trim(),
          password: regPassword,
        });
        window.location.hash = '#/profile';
      } catch (err) {
        setError(err.message || 'Đăng ký thất bại. Vui lòng thử lại với email/username khác.');
      } finally {
        setLoading(false);
      }
      return;
    }

    // Login Flow
    if (!username.trim() || !password.trim()) {
      setError('Vui lòng điền đầy đủ tên đăng nhập và mật khẩu.');
      return;
    }

    setLoading(true);
    try {
      const loggedUser = await login(username.trim(), password, { requireOperator: isAdminRoute });
      const roles = loggedUser?.roles || [];
      const isOp = roles.some((r) => ['ROLE_ADMIN', 'ADMIN', 'ROLE_STAFF', 'ROLE_ORDER_STAFF', 'ROLE_INVENTORY_STAFF', 'ROLE_CATALOG_STAFF', 'ROLE_CS_STAFF'].includes(r));
      const requestedAdmin = isAdminRoute;

      if (isOp && (requestedAdmin || roles.includes('ROLE_ADMIN') || roles.includes('ADMIN'))) {
        window.location.hash = '#/admin/dashboard';
      } else {
        window.location.hash = '#/profile';
      }
    } catch (err) {
      setError(err.message || 'Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.');
    } finally {
      setLoading(false);
    }
  };

  const handleQuickFill = (user) => {
    setAuthMode('login');
    setUsername(user);
    setError(null);
  };

  if (isAdminRoute) {
    return (
      <main className="admin-auth">
        <div className="admin-auth__ambient admin-auth__ambient--rose" aria-hidden="true" />
        <div className="admin-auth__ambient admin-auth__ambient--violet" aria-hidden="true" />

        <div className="admin-auth__shell">
          <section className="admin-auth__brand" aria-labelledby="admin-auth-brand-heading">
            <a href="#/" className="admin-auth__brandmark" aria-label="BeautyShop — về cửa hàng">
              <span className="admin-auth__logo"><Gem size={23} strokeWidth={1.5} /></span>
              <span>
                <strong>beautyshop<span>.</span></strong>
                <small>THE BEAUTY MANAGEMENT STUDIO</small>
              </span>
            </a>

            <div className="admin-auth__editorial">
              <span className="admin-auth__eyebrow"><span /> KHÔNG GIAN ĐIỀU HÀNH</span>
              <h1 id="admin-auth-brand-heading">Chăm từng chi tiết.<br /><em>Nâng tầm mỗi ngày.</em></h1>
              <p>Một không gian tinh gọn để kết nối đội ngũ, chăm sóc khách hàng và nuôi dưỡng sự phát triển.</p>
            </div>

            <div className="admin-auth__art" aria-hidden="true">
              <span className="admin-auth__orbit admin-auth__orbit--one" />
              <span className="admin-auth__orbit admin-auth__orbit--two" />
              <span className="admin-auth__orbit admin-auth__orbit--three" />
              <div className="admin-auth__sculpture">
                <span className="admin-auth__petal admin-auth__petal--one" />
                <span className="admin-auth__petal admin-auth__petal--two" />
                <span className="admin-auth__petal admin-auth__petal--three" />
                <span className="admin-auth__sculpture-core" />
              </div>
              <span className="admin-auth__star admin-auth__star--one" />
              <span className="admin-auth__star admin-auth__star--two" />
              <span className="admin-auth__art-label"><Sparkles size={14} /> Beauty in every detail</span>
              <span className="admin-auth__art-caption">CURATED WITH CARE — BEAUTYSHOP</span>
            </div>

            <div className="admin-auth__brand-footer">
              <span><ShieldCheck size={17} /> Truy cập theo phân quyền</span>
              <span className="admin-auth__brand-footer-line" />
              <span>Vận hành với sự an tâm</span>
            </div>
          </section>

          <section className="admin-auth__form-panel" aria-labelledby="admin-auth-heading">
            <a href="#/" className="admin-auth__back"><ArrowLeft size={16} /><span>Về cửa hàng</span><ArrowUpRight size={14} /></a>

            <div className="admin-auth__form-content">
              <div className="admin-auth__welcome-icon" aria-hidden="true"><ShieldCheck size={25} strokeWidth={1.5} /></div>
              <span className="admin-auth__form-eyebrow">BEAUTYSHOP ADMIN</span>
              <h2 id="admin-auth-heading">Chào mừng<br />bạn quay lại<span>.</span></h2>
              <p className="admin-auth__form-description">Đăng nhập để bắt đầu một ngày làm việc đầy cảm hứng.</p>

              {error && (
                <div id="admin-auth-error" className="admin-auth__error" role="alert">
                  <AlertCircle size={18} aria-hidden="true" />
                  <span>{error}</span>
                </div>
              )}

              <form onSubmit={handleSubmit} className="admin-auth__form" aria-busy={loading}>
                <div className="admin-auth__field">
                  <label htmlFor="admin-auth-username">Tên đăng nhập hoặc email</label>
                  <div className="admin-auth__input-wrap">
                    <User size={18} strokeWidth={1.7} aria-hidden="true" />
                    <input
                      id="admin-auth-username"
                      name="username"
                      type="text"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="Nhập tên đăng nhập của bạn"
                      autoComplete="username"
                      autoCapitalize="none"
                      spellCheck={false}
                      required
                      autoFocus={window.matchMedia('(min-width: 741px)').matches}
                      disabled={loading}
                      aria-describedby={error ? 'admin-auth-error' : undefined}
                    />
                  </div>
                </div>

                <div className="admin-auth__field">
                  <label htmlFor="admin-auth-password">Mật khẩu</label>
                  <div className="admin-auth__input-wrap">
                    <Lock size={18} strokeWidth={1.7} aria-hidden="true" />
                    <input
                      id="admin-auth-password"
                      name="password"
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="Nhập mật khẩu"
                      autoComplete="current-password"
                      required
                      disabled={loading}
                      aria-describedby={error ? 'admin-auth-error' : undefined}
                    />
                    <button
                      type="button"
                      className="admin-auth__password-toggle"
                      onClick={() => setShowPassword((visible) => !visible)}
                      aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiển thị mật khẩu'}
                      aria-pressed={showPassword}
                      disabled={loading}
                    >
                      {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                </div>

                <p className="admin-auth__access-note"><ShieldCheck size={14} aria-hidden="true" /> Dành cho quản trị viên và đội ngũ BeautyShop</p>

                <button type="submit" className="admin-auth__submit" disabled={loading}>
                  {loading ? <Loader2 className="admin-auth__loader" size={18} aria-hidden="true" /> : null}
                  <span>{loading ? 'Đang xác thực...' : 'Bắt đầu làm việc'}</span>
                  {!loading && <ArrowRight size={18} aria-hidden="true" />}
                </button>
                <span className="admin-auth__sr-only" role="status" aria-live="polite">{loading ? 'Đang xác thực tài khoản, vui lòng chờ.' : ''}</span>
              </form>

              <div className="admin-auth__help">
                <span className="admin-auth__help-line" />
                <span>CHĂM SÓC TỪNG ĐIỂM CHẠM</span>
                <span className="admin-auth__help-line" />
              </div>
              <p className="admin-auth__support">Cần hỗ trợ truy cập?<br /><span>Liên hệ quản trị viên để được hỗ trợ tài khoản.</span></p>
            </div>

            <div className="admin-auth__form-footer"><span>BeautyShop Management</span><span>Đẹp từ trải nghiệm.</span></div>
          </section>
        </div>
      </main>
    );
  }

  return (
    <div
      className="customer-app user-auth-fullscreen"
      style={{
        minHeight: '100vh',
        width: '100vw',
        display: 'flex',
        backgroundColor: '#FAF7F8',
        fontFamily: "'Be Vietnam Pro', 'Plus Jakarta Sans', sans-serif",
        overflowX: 'hidden',
        position: 'relative'
      }}
    >
      <style>{`
        @keyframes floatSlow1 {
          0%, 100% { transform: translateY(0px) rotate(0deg) scale(1); }
          50% { transform: translateY(-16px) rotate(8deg) scale(1.04); }
        }
        @keyframes floatSlow2 {
          0%, 100% { transform: translateY(0px) rotate(0deg) scale(1); }
          50% { transform: translateY(18px) rotate(-10deg) scale(0.96); }
        }
        @keyframes pulseGlow {
          0%, 100% { opacity: 0.35; transform: scale(1); }
          50% { opacity: 0.65; transform: scale(1.15); }
        }
        @keyframes driftOrb {
          0% { transform: translate(0, 0); }
          50% { transform: translate(25px, -35px); }
          100% { transform: translate(0, 0); }
        }
        /* Override .customer-app { flex-direction: column } from customer-theme.css */
        .customer-app.user-auth-fullscreen {
          flex-direction: row;
          max-width: none;
        }
        .user-auth-col-art {
          width: 28%;
          min-width: 320px;
          max-width: 420px;
          background: linear-gradient(165deg, #FFF0F3 0%, #FCE7EC 45%, #F8D5DE 100%);
          border-right: 1px solid #F0DEE3;
          position: relative;
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          padding: 44px 32px;
          overflow: hidden;
          z-index: 2;
        }
        .user-auth-col-forms {
          flex: 1;
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 40px 24px;
          position: relative;
          background: #FAF7F8;
          background-image:
            radial-gradient(circle at 90% 10%, rgba(212, 93, 121, 0.08) 0%, transparent 45%),
            radial-gradient(circle at 10% 90%, rgba(255, 240, 243, 0.7) 0%, transparent 45%);
        }
        .auth-slider-viewport {
          width: 100%;
          max-width: 580px;
          overflow: hidden;
          position: relative;
          background: #FFFFFF;
          border: 1px solid #F0DEE3;
          border-radius: 20px;
          box-shadow: 0 20px 45px -15px rgba(212, 93, 121, 0.15), 0 0 0 1px rgba(240, 222, 227, 0.5);
          transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .auth-slider-track {
          display: flex;
          align-items: stretch;
          width: 200%;
          transition: transform 0.45s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .auth-slide-pane {
          width: 50%;
          padding: 38px 40px;
          box-sizing: border-box;
          opacity: 0.35;
          transform: scale(0.97);
          transition: opacity 0.35s ease, transform 0.45s cubic-bezier(0.16, 1, 0.3, 1);
          display: flex;
          flex-direction: column;
          justify-content: space-between;
        }
        .auth-slide-pane.active {
          opacity: 1;
          transform: scale(1);
        }
        .beauty-floating-card {
          position: absolute;
          background: rgba(255, 255, 255, 0.85);
          backdrop-filter: blur(10px);
          border: 1px solid rgba(212, 93, 121, 0.2);
          border-radius: 14px;
          padding: 12px 16px;
          display: flex;
          align-items: center;
          gap: 12px;
          box-shadow: 0 10px 25px -5px rgba(212, 93, 121, 0.15);
          z-index: 3;
        }
        @media (max-width: 900px) {
          .customer-app.user-auth-fullscreen {
            flex-direction: column;
          }
          .user-auth-col-art {
            width: 100%;
            max-width: 100%;
            min-width: 0;
            padding: 28px 24px;
            border-right: none;
            border-bottom: 1px solid #F0DEE3;
          }
          .auth-slider-viewport {
            max-width: 100%;
          }
          .auth-slide-pane {
            padding: 28px 20px;
          }
        }
      `}</style>

      {/* ================= 1/4 COLUMN: BEAUTY COSMETICS ANIMATION ART ================= */}
      <section className="user-auth-col-art" aria-label="Khám phá BeautyShop">
        {/* Ambient Glowing Orbs */}
        <div style={{
          position: 'absolute',
          top: '-60px',
          left: '-40px',
          width: '240px',
          height: '240px',
          borderRadius: '50%',
          backgroundColor: 'rgba(212, 93, 121, 0.22)',
          filter: 'blur(55px)',
          animation: 'driftOrb 12s ease-in-out infinite'
        }} />
        <div style={{
          position: 'absolute',
          bottom: '10%',
          right: '-40px',
          width: '200px',
          height: '200px',
          borderRadius: '50%',
          backgroundColor: 'rgba(255, 179, 198, 0.45)',
          filter: 'blur(50px)',
          animation: 'pulseGlow 8s ease-in-out infinite'
        }} />

        {/* Brand Header */}
        <div style={{ position: 'relative', zIndex: 4 }}>
          <a href="#/" style={{ textDecoration: 'none', display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
            <div
              style={{
                width: '42px',
                height: '42px',
                borderRadius: '12px',
                backgroundColor: '#D45D79',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 6px 16px rgba(212, 93, 121, 0.35)',
                color: '#FFFFFF'
              }}
            >
              <Sparkles size={20} />
            </div>
            <div>
              <div style={{ fontSize: '18px', fontWeight: 800, color: '#D45D79', letterSpacing: '-0.02em', lineHeight: 1.2 }}>
                BEAUTYSHOP
              </div>
              <div style={{ fontSize: '10.5px', color: '#8C2A47', fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase' }}>
                Dược Mỹ Phẩm & Spa Clinic
              </div>
            </div>
          </a>

          <h2 style={{ fontSize: '22px', fontWeight: 800, color: '#1F2937', lineHeight: 1.35, margin: '0 0 10px 0' }}>
            Vẻ đẹp khoa học.<br />
            <span style={{ color: '#D45D79' }}>Chăm sóc chuẩn Y khoa.</span>
          </h2>
          <p style={{ fontSize: '12.5px', color: '#6B7280', margin: 0, lineHeight: 1.6 }}>
            Trải nghiệm nền tảng phân phối dược mỹ phẩm chính hãng và liệu trình clinic phục hồi da chuyên sâu.
          </p>
        </div>

        {/* Middle Canvas: Animated Cosmetics Floating Badges */}
        <div style={{ position: 'relative', height: '340px', margin: '20px 0', zIndex: 3 }}>
          {/* Card 1: Serum & Skincare Routine */}
          <div
            className="beauty-floating-card"
            style={{
              top: '10px',
              left: '0px',
              animation: 'floatSlow1 7s ease-in-out infinite'
            }}
          >
            <div style={{
              width: '38px',
              height: '38px',
              borderRadius: '10px',
              backgroundColor: '#FFF0F3',
              color: '#D45D79',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '18px'
            }}>
              <Sparkles size={18} />
            </div>
            <div>
              <div style={{ fontSize: '12.5px', fontWeight: 800, color: '#1F2937' }}>Dược Mỹ Phẩm Serum</div>
              <div style={{ fontSize: '11px', color: '#6B7280' }}>Phục hồi & Trẻ hóa làn da</div>
            </div>
          </div>

          {/* Card 2: Makeup & Lipstick */}
          <div
            className="beauty-floating-card"
            style={{
              top: '115px',
              right: '0px',
              animation: 'floatSlow2 8.5s ease-in-out infinite'
            }}
          >
            <div style={{
              width: '38px',
              height: '38px',
              borderRadius: '10px',
              backgroundColor: '#FFF0F3',
              color: '#D45D79',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '18px'
            }}>
              <Gem size={18} />
            </div>
            <div>
              <div style={{ fontSize: '12.5px', fontWeight: 800, color: '#1F2937' }}>Makeup & Son Dưỡng</div>
              <div style={{ fontSize: '11px', color: '#6B7280' }}>Tone da tự nhiên, rạng ngời</div>
            </div>
          </div>

          {/* Card 3: Spa Treatment & Mask */}
          <div
            className="beauty-floating-card"
            style={{
              bottom: '25px',
              left: '15px',
              animation: 'floatSlow1 9s ease-in-out infinite 1s'
            }}
          >
            <div style={{
              width: '38px',
              height: '38px',
              borderRadius: '10px',
              backgroundColor: '#FFF0F3',
              color: '#D45D79',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '18px'
            }}>
              <Sparkles size={18} />
            </div>
            <div>
              <div style={{ fontSize: '12.5px', fontWeight: 800, color: '#1F2937' }}>Spa Clinic Y Khoa</div>
              <div style={{ fontSize: '11px', color: '#6B7280' }}>Bác sĩ chuyên khoa thăm khám</div>
            </div>
          </div>
        </div>

        {/* Art Column Footer */}
        <div style={{ position: 'relative', zIndex: 4, borderTop: '1px solid #F0DEE3', paddingTop: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '11.5px', color: '#8C2A47', fontWeight: 600 }}>
            <ShieldCheck size={14} color="#059669" />
            <span>Cam kết 100% hàng chính hãng có tem phụ</span>
          </div>
          <div style={{ fontSize: '11px', color: '#9CA3AF', marginTop: '4px' }}>
            Hotline tư vấn da liễu miễn phí: 1800 6324
          </div>
        </div>
      </section>

      {/* ================= 3/4 COLUMN: SLIDING FORMS (LOGIN / REGISTER) ================= */}
      <main className="user-auth-col-forms">
        {/* Top Floating Back Link */}
        <div style={{ width: '100%', maxWidth: '580px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px' }}>
          <a
            href="#/"
            style={{
              textDecoration: 'none',
              color: '#D45D79',
              fontSize: '13px',
              fontWeight: 700,
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              transition: 'color 0.15s ease'
            }}
          >
            <ArrowLeft size={14} />
            <span>Quay lại cửa hàng</span>
          </a>

          <span style={{ fontSize: '12px', color: '#6B7280' }}>
            {authMode === 'login' ? 'Chưa có tài khoản?' : 'Đã có tài khoản?'}
            <button
              type="button"
              onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setError(null); }}
              style={{
                background: 'none',
                border: 'none',
                color: '#D45D79',
                fontWeight: 800,
                fontSize: '12px',
                cursor: 'pointer',
                marginLeft: '6px',
                textDecoration: 'underline'
              }}
            >
              {authMode === 'login' ? 'Đăng ký ngay' : 'Đăng nhập'}
            </button>
          </span>
        </div>

        {/* Viewport for smooth transform slide */}
        <div className="auth-slider-viewport">
          {/* Header Switcher Tabs */}
          <div style={{
            display: 'flex',
            backgroundColor: '#FAF7F8',
            borderBottom: '1px solid #F0DEE3',
            padding: '6px',
            gap: '6px'
          }}>
            <button
              type="button"
              onClick={() => { setAuthMode('login'); setError(null); }}
              style={{
                flex: 1,
                padding: '10px 0',
                border: 'none',
                borderRadius: '12px',
                backgroundColor: authMode === 'login' ? '#FFFFFF' : 'transparent',
                color: authMode === 'login' ? '#D45D79' : '#6B7280',
                fontWeight: authMode === 'login' ? 800 : 600,
                fontSize: '13.5px',
                boxShadow: authMode === 'login' ? '0 2px 8px rgba(212, 93, 121, 0.12)' : 'none',
                cursor: 'pointer',
                transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px'
              }}
            >
              <ArrowRight size={14} />
              <span>Đăng Nhập</span>
            </button>

            <button
              type="button"
              onClick={() => { setAuthMode('register'); setError(null); }}
              style={{
                flex: 1,
                padding: '10px 0',
                border: 'none',
                borderRadius: '12px',
                backgroundColor: authMode === 'register' ? '#FFFFFF' : 'transparent',
                color: authMode === 'register' ? '#D45D79' : '#6B7280',
                fontWeight: authMode === 'register' ? 800 : 600,
                fontSize: '13.5px',
                boxShadow: authMode === 'register' ? '0 2px 8px rgba(212, 93, 121, 0.12)' : 'none',
                cursor: 'pointer',
                transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px'
              }}
            >
              <User size={14} />
              <span>Đăng Ký Tài Khoản</span>
            </button>
          </div>

          {/* Sliding Track: transformX 0% (login) vs -50% (register) */}
          <div
            className="auth-slider-track"
            style={{
              transform: authMode === 'login' ? 'translateX(0%)' : 'translateX(-50%)'
            }}
          >
            {/* PANE 1: LOGIN FORM */}
            <div className={`auth-slide-pane ${authMode === 'login' ? 'active' : ''}`}>
              <div>
                {/* Form Logo Header */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
                  <div style={{
                    width: '36px',
                    height: '36px',
                    borderRadius: '10px',
                    backgroundColor: '#FFF0F3',
                    color: '#D45D79',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '16px',
                    boxShadow: '0 2px 8px rgba(212, 93, 121, 0.2)'
                  }}>
                    <Sparkles size={18} />
                  </div>
                  <div>
                    <div style={{ fontSize: '15px', fontWeight: 800, color: '#D45D79', letterSpacing: '-0.02em', lineHeight: 1.1 }}>
                      BEAUTYSHOP
                    </div>
                    <div style={{ fontSize: '9.5px', color: '#8C2A47', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                      Cổng Đăng Nhập
                    </div>
                  </div>
                </div>

                <div style={{ marginBottom: '22px' }}>
                  <h3 style={{ fontSize: '22px', fontWeight: 800, color: '#1F2937', margin: '0 0 6px 0' }}>
                    Chào mừng bạn quay lại
                  </h3>
                  <p style={{ fontSize: '13px', color: '#6B7280', margin: 0 }}>
                    Đăng nhập để theo dõi đơn hàng và ưu đãi liệu trình Spa của bạn.
                  </p>
                </div>

                {error && authMode === 'login' && (
                  <div style={{
                    padding: '12px 14px',
                    backgroundColor: '#FFF1F2',
                    border: '1px solid #FECDD3',
                    borderRadius: '10px',
                    color: '#BE123C',
                    fontSize: '12.5px',
                    marginBottom: '18px',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '10px'
                  }}>
                    <AlertCircle size={14} style={{ marginTop: '2px' }} />
                    <div>{error}</div>
                  </div>
                )}

                <form onSubmit={handleSubmit}>
                  <div style={{ marginBottom: '16px' }}>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: '#4B5563', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: '6px' }}>
                      Tên đăng nhập hoặc Email *
                    </label>
                    <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                      <User size={14} style={{ position: 'absolute', left: '14px', color: '#9CA3AF', pointerEvents: 'none' }} />
                      <input
                        type="text"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        placeholder="Nhập username hoặc email"
                        required
                        style={{
                          width: '100%',
                          padding: '12px 14px 12px 40px',
                          fontSize: '14px',
                          color: '#1F2937',
                          backgroundColor: '#FAF7F8',
                          border: '1px solid #F0DEE3',
                          borderRadius: '10px',
                          outline: 'none',
                          transition: 'all 0.2s'
                        }}
                        onFocus={(e) => {
                          e.target.style.backgroundColor = '#FFFFFF';
                          e.target.style.borderColor = '#D45D79';
                          e.target.style.boxShadow = '0 0 0 3px rgba(212, 93, 121, 0.15)';
                        }}
                        onBlur={(e) => {
                          e.target.style.backgroundColor = '#FAF7F8';
                          e.target.style.borderColor = '#F0DEE3';
                          e.target.style.boxShadow = 'none';
                        }}
                      />
                    </div>
                  </div>

                  <div style={{ marginBottom: '22px' }}>
                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: '#4B5563', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: '6px' }}>
                      Mật khẩu *
                    </label>
                    <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                      <Lock size={14} style={{ position: 'absolute', left: '14px', color: '#9CA3AF', pointerEvents: 'none' }} />
                      <input
                        type={showPassword ? 'text' : 'password'}
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        placeholder="••••••••"
                        required
                        style={{
                          width: '100%',
                          padding: '12px 40px 12px 40px',
                          fontSize: '14px',
                          color: '#1F2937',
                          backgroundColor: '#FAF7F8',
                          border: '1px solid #F0DEE3',
                          borderRadius: '10px',
                          outline: 'none',
                          transition: 'all 0.2s'
                        }}
                        onFocus={(e) => {
                          e.target.style.backgroundColor = '#FFFFFF';
                          e.target.style.borderColor = '#D45D79';
                          e.target.style.boxShadow = '0 0 0 3px rgba(212, 93, 121, 0.15)';
                        }}
                        onBlur={(e) => {
                          e.target.style.backgroundColor = '#FAF7F8';
                          e.target.style.borderColor = '#F0DEE3';
                          e.target.style.boxShadow = 'none';
                        }}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        style={{
                          position: 'absolute',
                          right: '12px',
                          background: 'none',
                          border: 'none',
                          color: '#9CA3AF',
                          cursor: 'pointer',
                          padding: '4px'
                        }}
                      >
                        {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                      </button>
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={loading}
                    style={{
                      width: '100%',
                      height: '48px',
                      fontSize: '14px',
                      fontWeight: 700,
                      borderRadius: '10px',
                      backgroundColor: '#D45D79',
                      color: '#FFFFFF',
                      border: 'none',
                      cursor: loading ? 'not-allowed' : 'pointer',
                      boxShadow: '0 4px 14px rgba(212, 93, 121, 0.35)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '8px',
                      transition: 'all 0.2s ease'
                    }}
                    onMouseEnter={(e) => {
                      if (!loading) e.currentTarget.style.backgroundColor = '#B84365';
                    }}
                    onMouseLeave={(e) => {
                      if (!loading) e.currentTarget.style.backgroundColor = '#D45D79';
                    }}
                  >
                    {loading ? (
                      <>
                        <Loader2 size={18} className="animate-spin" />
                        <span>Đang xác thực...</span>
                      </>
                    ) : (
                      <>
                        <span>Đăng nhập ngay</span>
                        <ArrowRight size={14} />
                      </>
                    )}
                  </button>
                </form>
              </div>
            </div>

            {/* PANE 2: REGISTER FORM */}
            <div className={`auth-slide-pane ${authMode === 'register' ? 'active' : ''}`}>
              <div>
                {/* Form Logo Header */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
                  <div style={{
                    width: '36px',
                    height: '36px',
                    borderRadius: '10px',
                    backgroundColor: '#FFF0F3',
                    color: '#D45D79',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '16px',
                    boxShadow: '0 2px 8px rgba(212, 93, 121, 0.2)'
                  }}>
                    <Sparkles size={18} />
                  </div>
                  <div>
                    <div style={{ fontSize: '15px', fontWeight: 800, color: '#D45D79', letterSpacing: '-0.02em', lineHeight: 1.1 }}>
                      BEAUTYSHOP
                    </div>
                    <div style={{ fontSize: '9.5px', color: '#8C2A47', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                      Tạo Tài Khoản
                    </div>
                  </div>
                </div>

                <div style={{ marginBottom: '18px' }}>
                  <h3 style={{ fontSize: '22px', fontWeight: 800, color: '#1F2937', margin: '0 0 6px 0' }}>
                    Đăng ký tài khoản hội viên
                  </h3>
                  <p style={{ fontSize: '13px', color: '#6B7280', margin: 0 }}>
                    Nhận ngay voucher 10% và tích điểm hoàn tiền cho mỗi đơn hàng.
                  </p>
                </div>

                {error && authMode === 'register' && (
                  <div style={{
                    padding: '12px 14px',
                    backgroundColor: '#FFF1F2',
                    border: '1px solid #FECDD3',
                    borderRadius: '10px',
                    color: '#BE123C',
                    fontSize: '12.5px',
                    marginBottom: '18px',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '10px'
                  }}>
                    <AlertCircle size={14} style={{ marginTop: '2px' }} />
                    <div>{error}</div>
                  </div>
                )}

              <form onSubmit={handleSubmit}>
                <div style={{ marginBottom: '12px' }}>
                  <label style={{ display: 'block', fontSize: '11.5px', fontWeight: 700, color: '#4B5563', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: '4px' }}>
                    Họ và tên *
                  </label>
                  <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                    <User size={13} style={{ position: 'absolute', left: '12px', color: '#9CA3AF', pointerEvents: 'none' }} />
                    <input
                      type="text"
                      value={regFullName}
                      onChange={(e) => setRegFullName(e.target.value)}
                      placeholder="Nguyễn Văn A"
                      required
                      style={{
                        width: '100%',
                        padding: '10px 12px 10px 36px',
                        fontSize: '13.5px',
                        backgroundColor: '#FAF7F8',
                        border: '1px solid #F0DEE3',
                        borderRadius: '10px',
                        outline: 'none',
                        transition: 'all 0.2s'
                      }}
                      onFocus={(e) => {
                        e.target.style.backgroundColor = '#FFFFFF';
                        e.target.style.borderColor = '#D45D79';
                        e.target.style.boxShadow = '0 0 0 3px rgba(212, 93, 121, 0.15)';
                      }}
                      onBlur={(e) => {
                        e.target.style.backgroundColor = '#FAF7F8';
                        e.target.style.borderColor = '#F0DEE3';
                        e.target.style.boxShadow = 'none';
                      }}
                    />
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '12px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '11.5px', fontWeight: 700, color: '#4B5563', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: '4px' }}>
                      Email *
                    </label>
                    <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                      <Mail size={13} style={{ position: 'absolute', left: '11px', color: '#9CA3AF', pointerEvents: 'none' }} />
                      <input
                        type="email"
                        value={regEmail}
                        onChange={(e) => setRegEmail(e.target.value)}
                        placeholder="email@domain.com"
                        required
                        style={{
                          width: '100%',
                          padding: '10px 10px 10px 34px',
                          fontSize: '13px',
                          backgroundColor: '#FAF7F8',
                          border: '1px solid #F0DEE3',
                          borderRadius: '10px',
                          outline: 'none'
                        }}
                      />
                    </div>
                  </div>

                  <div>
                    <label style={{ display: 'block', fontSize: '11.5px', fontWeight: 700, color: '#4B5563', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: '4px' }}>
                      Số điện thoại
                    </label>
                    <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                      <Phone size={13} style={{ position: 'absolute', left: '11px', color: '#9CA3AF', pointerEvents: 'none' }} />
                      <input
                        type="tel"
                        value={regPhone}
                        onChange={(e) => setRegPhone(e.target.value)}
                        placeholder="0912345678"
                        style={{
                          width: '100%',
                          padding: '10px 10px 10px 34px',
                          fontSize: '13px',
                          backgroundColor: '#FAF7F8',
                          border: '1px solid #F0DEE3',
                          borderRadius: '10px',
                          outline: 'none'
                        }}
                      />
                    </div>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginBottom: '18px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '11.5px', fontWeight: 700, color: '#4B5563', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: '4px' }}>
                      Tên tài khoản *
                    </label>
                    <input
                      type="text"
                      value={regUsername}
                      onChange={(e) => setRegUsername(e.target.value)}
                      placeholder="nguyena"
                      required
                      style={{
                        width: '100%',
                        padding: '10px 12px',
                        fontSize: '13px',
                        backgroundColor: '#FAF7F8',
                        border: '1px solid #F0DEE3',
                        borderRadius: '10px',
                        outline: 'none'
                      }}
                    />
                  </div>

                  <div>
                    <label style={{ display: 'block', fontSize: '11.5px', fontWeight: 700, color: '#4B5563', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: '4px' }}>
                      Mật khẩu *
                    </label>
                    <input
                      type="password"
                      value={regPassword}
                      onChange={(e) => setRegPassword(e.target.value)}
                      placeholder="Tối thiểu 6 ký tự"
                      required
                      style={{
                        width: '100%',
                        padding: '10px 12px',
                        fontSize: '13px',
                        backgroundColor: '#FAF7F8',
                        border: '1px solid #F0DEE3',
                        borderRadius: '10px',
                        outline: 'none'
                      }}
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  style={{
                    width: '100%',
                    height: '48px',
                    fontSize: '14px',
                    fontWeight: 700,
                    borderRadius: '10px',
                    backgroundColor: '#D45D79',
                    color: '#FFFFFF',
                    border: 'none',
                    cursor: loading ? 'not-allowed' : 'pointer',
                    boxShadow: '0 4px 14px rgba(212, 93, 121, 0.35)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '8px',
                    transition: 'all 0.2s ease'
                  }}
                  onMouseEnter={(e) => {
                    if (!loading) e.currentTarget.style.backgroundColor = '#B84365';
                  }}
                  onMouseLeave={(e) => {
                    if (!loading) e.currentTarget.style.backgroundColor = '#D45D79';
                  }}
                >
                  {loading ? (
                    <>
                      <Loader2 size={18} className="animate-spin" />
                      <span>Đang tạo tài khoản...</span>
                    </>
                  ) : (
                    <>
                      <span>Hoàn tất đăng ký</span>
                      <ArrowRight size={14} />
                    </>
                  )}
                </button>
              </form>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};
