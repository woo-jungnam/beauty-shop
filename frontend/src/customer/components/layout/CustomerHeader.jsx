import React, { useState, useRef, useEffect, useMemo } from 'react';
import {
  Home,
  Sparkles,
  Crown,
  Flame,
  Zap,
  Calendar,
  Stethoscope,
  Search,
  ShoppingBag,
  User,
  ChevronDown,
  ChevronRight,
  Menu,
  X,
  ShieldCheck,
  Truck,
  RotateCcw,
  Phone,
  PhoneCall,
  MapPin,
  Package,
  LogOut,
  LayoutDashboard,
  Tag,
  Heart,
  Droplets,
  Layers,
  ArrowRight,
  Ticket,
  Check,
  Building2
} from 'lucide-react';
import { useCustomerCart } from '../../stores/customerCartStore';
import { useAuth } from '../../../app/providers/AuthProvider';
import { apiClient } from '../../../shared/api/client';
import { cleanDisplayName } from '../../../shared/utils/formatters';

export const CustomerHeader = ({ onNavigate, currentRoute = 'home' }) => {
  const { totalItems, setCartOpen } = useCustomerCart();
  const { user, isAuthenticated, isOperator, logout } = useAuth();

  const [searchKeyword, setSearchKeyword] = useState('');
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  const [categoryMenuOpen, setCategoryMenuOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  // Dynamic Navigation & Catalog Metadata from Backend APIs
  const [allCategories, setAllCategories] = useState([]);
  const [topBrands, setTopBrands] = useState([]);
  const [spaServices, setSpaServices] = useState([]);
  const [activeDropdownTab, setActiveDropdownTab] = useState(null);
  const [hoveredLevel1, setHoveredLevel1] = useState('skincare');

  const categoryRef = useRef(null);
  const searchRef = useRef(null);
  const userMenuRef = useRef(null);
  const headerRef = useRef(null);
  const subnavDropdownTimer = useRef(null);

  useEffect(() => {
    const updateHeaderHeight = () => {
      if (headerRef.current) {
        const height = headerRef.current.offsetHeight;
        document.documentElement.style.setProperty('--customer-header-height', `${height}px`);
      }
    };
    updateHeaderHeight();
    window.addEventListener('resize', updateHeaderHeight);
    return () => window.removeEventListener('resize', updateHeaderHeight);
  }, []);

  // Fetch dynamic categories, brands, and spa services from backend
  useEffect(() => {
    let isCancelled = false;
    const fetchNavData = async () => {
      try {
        const [catRes, brandRes, spaRes] = await Promise.allSettled([
          apiClient.get('/api/v1/categories'),
          apiClient.get('/api/v1/brands?size=12'),
          apiClient.get('/api/v1/spa/services')
        ]);

        if (!isCancelled) {
          if (catRes.status === 'fulfilled') {
            const list = catRes.value?.data || catRes.value || [];
            setAllCategories(Array.isArray(list) ? list : []);
          }
          if (brandRes.status === 'fulfilled') {
            const bList = brandRes.value?.data?.content || brandRes.value?.content || [];
            setTopBrands(Array.isArray(bList) ? bList : []);
          }
          if (spaRes.status === 'fulfilled') {
            const sList = spaRes.value?.data || spaRes.value || [];
            setSpaServices(Array.isArray(sList) ? sList : []);
          }
        }
      } catch (err) {
        console.error('Failed to load navigation metadata', err);
      }
    };
    fetchNavData();
    return () => { isCancelled = true; };
  }, []);

  // Close dropdowns when clicking outside
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (categoryRef.current && !categoryRef.current.contains(e.target)) {
        setCategoryMenuOpen(false);
      }
      if (searchRef.current && !searchRef.current.contains(e.target)) {
        setIsSearchFocused(false);
      }
      if (userMenuRef.current && !userMenuRef.current.contains(e.target)) {
        setUserMenuOpen(false);
      }
      if (!e.target.closest('.customer-subnav-dropdown-wrapper')) {
        setActiveDropdownTab(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSearchSubmit = (e) => {
    if (e) e.preventDefault();
    if (searchKeyword.trim()) {
      setIsSearchFocused(false);
      onNavigate(`products?search=${encodeURIComponent(searchKeyword.trim())}`);
    }
  };

  const handleQuickKeyword = (kw) => {
    setSearchKeyword(kw);
    setIsSearchFocused(false);
    onNavigate(`products?search=${encodeURIComponent(kw)}`);
  };

  // Smooth scrolling for hash links
  const handleNavClick = (route) => {
    setCategoryMenuOpen(false);
    setMobileMenuOpen(false);
    setActiveDropdownTab(null);

    if (route.startsWith('#')) {
      const targetSelector = route;
      const doScroll = () => {
        const el = document.querySelector(targetSelector);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'start' });
          el.classList.add('nav-highlight-target');
          setTimeout(() => el.classList.remove('nav-highlight-target'), 2000);
          return true;
        }
        return false;
      };

      if (!currentRoute || currentRoute === '' || currentRoute === 'home') {
        if (!doScroll()) {
          setTimeout(doScroll, 120);
        }
      } else {
        onNavigate('');
        let attempts = 0;
        const timer = setInterval(() => {
          attempts++;
          if (doScroll() || attempts >= 10) {
            clearInterval(timer);
          }
        }, 120);
      }
    } else {
      onNavigate(route);
    }
  };

  // Group all categories dynamically into medical skincare domains
  const categorizedGroups = useMemo(() => {
    const skincare = [];
    const makeup = [];
    const bodyAndHair = [];
    const toolsAndOthers = [];

    allCategories.forEach((cat) => {
      const slug = (cat.slug || '').toLowerCase();
      const name = (cat.name || '').toLowerCase();

      if (
        slug.includes('mat') || slug.includes('da') || slug.includes('serum') ||
        slug.includes('rua-mat') || slug.includes('tay-trang') || slug.includes('toner') ||
        slug.includes('chong-nang') || slug.includes('duong-am') || slug.includes('mun') ||
        name.includes('da') || name.includes('mặt') || name.includes('serum')
      ) {
        skincare.push(cat);
      } else if (
        slug.includes('son') || slug.includes('trang-diem') || slug.includes('phan') ||
        slug.includes('mat-na') || name.includes('son') || name.includes('trang điểm')
      ) {
        makeup.push(cat);
      } else if (
        slug.includes('body') || slug.includes('toc') || slug.includes('tam') ||
        slug.includes('co-the') || name.includes('tóc') || name.includes('tắm')
      ) {
        bodyAndHair.push(cat);
      } else {
        toolsAndOthers.push(cat);
      }
    });

    return { skincare, makeup, bodyAndHair, toolsAndOthers };
  }, [allCategories]);

  // Dynamic Trending Keywords from Real DB Categories & Brands
  const popularKeywords = useMemo(() => {
    const list = [];
    if (topBrands.length > 0) {
      topBrands.slice(0, 3).forEach(b => {
        const cleaned = cleanDisplayName(b.name);
        if (cleaned && !list.includes(cleaned)) list.push(cleaned);
      });
    }
    if (allCategories.length > 0) {
      allCategories.slice(0, 4).forEach(c => {
        const cleaned = cleanDisplayName(c.name);
        if (cleaned && list.length < 8 && !list.includes(cleaned)) list.push(cleaned);
      });
    }
    return list;
  }, [allCategories, topBrands]);

  // Standardized Hasaki UX/UI Subnavigation Structure
  const subnavItems = useMemo(() => {
    return [
      {
        id: 'home',
        label: 'Trang chủ',
        route: '',
        Icon: Home
      },
      {
        id: 'flash-sale',
        label: 'Flash Deals',
        route: '#flash-sale',
        Icon: Zap,
        color: '#E31837',
        highlightBadge: 'HOT'
      },
      {
        id: 'products',
        label: 'Dược Mỹ Phẩm',
        route: 'products',
        Icon: Sparkles,
        hasDropdown: true,
        dropdownType: 'categories'
      },
      {
        id: 'brands',
        label: 'Thương Hiệu',
        route: 'products',
        Icon: Crown,
        hasDropdown: true,
        dropdownType: 'brands'
      },
      {
        id: 'spa-services',
        label: 'Clinic & Spa',
        route: '#spa-section',
        Icon: Stethoscope,
        hasDropdown: true,
        dropdownType: 'spa'
      },
      {
        id: 'best-seller',
        label: 'Bán Chạy Nhất',
        route: 'products?sort=popular&filter=best-seller&name=Bán+Chạy+Nhất',
        Icon: Flame,
        color: '#326E51',
        highlightBadge: 'TOP'
      },
      {
        id: 'store-about',
        label: 'Hệ Thống Cửa Hàng',
        route: '#store-about',
        Icon: Building2
      },
      {
        id: 'booking',
        label: 'Đặt Lịch Hẹn Spa',
        route: 'booking',
        Icon: Calendar,
        highlightBadge: 'VIP'
      },
    ];
  }, []);

  // Multi-Level Hierarchical Mega Navigation Structure
  const megaHierarchy = useMemo(() => {
    return {
      skincare: {
        id: 'skincare',
        label: 'Chăm Sóc Da Mặt',
        icon: Sparkles,
        columns: [
          {
            title: 'Làm Sạch Da Mặt',
            items: [
              { label: 'Sữa Rửa Mặt Tạo Bọt', query: 'products?search=sữa+rửa+mặt' },
              { label: 'Nước Tẩy Trang Micellar', query: 'products?search=tẩy+trang' },
              { label: 'Dầu & Sáp Tẩy Trang', query: 'products?search=dầu+tẩy+trang' },
              { label: 'Tẩy Tế Bào Chết Mặt', query: 'products?search=tẩy+tế+bào+chết' },
            ]
          },
          {
            title: 'Cân Bằng & Tinh Chất',
            items: [
              { label: 'Nước Hoa Hồng / Toner', query: 'products?search=toner' },
              { label: 'Serum B5 Phục Hồi Màng Da', query: 'products?search=serum+b5' },
              { label: 'Tinh Chất Niacinamide 10%', query: 'products?search=niacinamide' },
              { label: 'Serum Vitamin C Sáng Da', query: 'products?search=vitamin+c' },
            ]
          },
          {
            title: 'Dưỡng Ẩm & Chống Nắng',
            items: [
              { label: 'Kem Dưỡng Ẩm Màng Da', query: 'products?search=kem+dưỡng' },
              { label: 'Kem Chống Nắng Phổ Rộng', query: 'products?search=chống+nắng' },
              { label: 'Xịt Khoáng Cấp Ẩm Dịu Da', query: 'products?search=xịt+khoáng' },
              { label: 'Mặt Nạ Đất Sét Hút Dầu', query: 'products?search=mặt+nạ' },
            ]
          }
        ],
        featuredBrands: ['La Roche-Posay', 'CeraVe', 'Vichy', 'Bioderma']
      },
      treatment: {
        id: 'treatment',
        label: 'Đặc Trị Mụn & Y Khoa',
        icon: Stethoscope,
        columns: [
          {
            title: 'Điều Trị Mụn Viêm',
            items: [
              { label: 'Kem Chấm Mụn Viêm Sưng', query: 'products?search=chấm+mụn' },
              { label: 'Tinh Chất BHA 2% Thu Chân Lông', query: 'products?search=bha' },
              { label: 'Gel Rửa Mặt Kháng Khuẩn', query: 'products?search=kháng+khuẩn' },
              { label: 'Miếng Dán Mụn Kháng Nước', query: 'products?search=dán+mụn' },
            ]
          },
          {
            title: 'Phục Hồi Hàng Rào Da',
            items: [
              { label: 'Kem Dưỡng Ceramide NP', query: 'products?search=ceramide' },
              { label: 'Serum Rau Má Centella', query: 'products?search=centella' },
              { label: 'Tinh Chất Sau Peel & Laser', query: 'products?search=phục+hồi' },
              { label: 'Kem Làm Dịu Kích Ứng', query: 'products?search=dịu+da' },
            ]
          },
          {
            title: 'Chống Lão Hóa & Mờ Thâm',
            items: [
              { label: 'Retinol Vi Nang 0.5% - 1%', query: 'products?search=retinol' },
              { label: 'Tinh Chất Tranexamic Acid', query: 'products?search=tranexamic' },
              { label: 'Peptide Nâng Cơ Màng Da', query: 'products?search=peptide' },
              { label: 'Kem Dưỡng Chống Nhăn', query: 'products?search=chống+lão+hóa' },
            ]
          }
        ],
        featuredBrands: ["Paula's Choice", 'Obagi Medical', 'SkinCeuticals', 'SVR']
      },
      makeup: {
        id: 'makeup',
        label: 'Trang Điểm',
        icon: Tag,
        columns: [
          {
            title: 'Trang Điểm Mặt',
            items: [
              { label: 'Phấn Nước Cushion Mỏng Mịn', query: 'products?search=cushion' },
              { label: 'Kem Nền Kiềm Dầu 24H', query: 'products?search=kem+nền' },
              { label: 'Phấn Phủ Bột Khoáng', query: 'products?search=phấn+phủ' },
              { label: 'Kem Lót Kiềm Dầu Primer', query: 'products?search=kem+lót' },
            ]
          },
          {
            title: 'Trang Điểm Môi',
            items: [
              { label: 'Son Kem Lì Lâu Trôi', query: 'products?search=son+kem' },
              { label: 'Son Dưỡng Ẩm Có Màu', query: 'products?search=son+dưỡng' },
              { label: 'Son Tint Bóng Căng Mọng', query: 'products?search=son+tint' },
              { label: 'Tẩy Tế Bào Chết Môi', query: 'products?search=tẩy+da+chết+môi' },
            ]
          },
          {
            title: 'Trang Điểm Mắt & Mày',
            items: [
              { label: 'Mascara Dài Mi Chống Nước', query: 'products?search=mascara' },
              { label: 'Bút Kẻ Mắt Nước Eyeliner', query: 'products?search=eyeliner' },
              { label: 'Chì Kẻ Mày Định Hình', query: 'products?search=kẻ+mày' },
              { label: 'Bảng Phấn Mắt Tone Nude', query: 'products?search=phấn+mắt' },
            ]
          }
        ],
        featuredBrands: ['Maybelline', "L'Oreal", 'Innisfree', 'Romand']
      },
      body_hair: {
        id: 'body_hair',
        label: 'Chăm Sóc Cơ Thể & Tóc',
        icon: Droplets,
        columns: [
          {
            title: 'Chăm Sóc Tóc',
            items: [
              { label: 'Dầu Gội Sạch Gàu & Nấm', query: 'products?search=dầu+gội' },
              { label: 'Dầu Xả Phục Hồi Hư Tổn', query: 'products?search=dầu+xả' },
              { label: 'Tinh Dầu Dưỡng Tóc Argan', query: 'products?search=dưỡng+tóc' },
              { label: 'Serum Kích Mọc Tóc Bưởi', query: 'products?search=mọc+tóc' },
            ]
          },
          {
            title: 'Chăm Sóc Cơ Thể',
            items: [
              { label: 'Sữa Tắm Dưỡng Ẩm Toàn Thân', query: 'products?search=sữa+tắm' },
              { label: 'Sữa Dưỡng Thể Trắng Da', query: 'products?search=dưỡng+thể' },
              { label: 'Tẩy Da Chết Body Cà Phê', query: 'products?search=tẩy+tế+bào+chết+body' },
              { label: 'Lăn Khử Mùi & Ngăn Mồ Hôi', query: 'products?search=lăn+khử+mùi' },
            ]
          },
          {
            title: 'Chăm Sóc Tay & Chân',
            items: [
              { label: 'Kem Dưỡng Ẩm Da Tay', query: 'products?search=kem+tay' },
              { label: 'Tẩy Tế Bào Chết Gót Chân', query: 'products?search=gót+chân' },
              { label: 'Xịt Chống Nắng Toàn Thân', query: 'products?search=xịt+chống+nắng' },
            ]
          }
        ],
        featuredBrands: ['Vaseline', 'Cocoon', 'Dove', 'TRESemmé']
      },
      spa_clinic: {
        id: 'spa_clinic',
        label: 'Beauty Clinic & Spa',
        icon: Building2,
        columns: [
          {
            title: 'Chăm Sóc & Làm Sạch',
            items: [
              { label: 'Aqua Peel Hút Mụn Chân Không', query: 'booking' },
              { label: 'Chăm Sóc Da Y Khoa 60 Phút', query: 'booking' },
              { label: 'Điện Di Tinh Chất Vitamin C', query: 'booking' },
              { label: 'Thải Độc Da Oxy Tươi Đa Tầng', query: 'booking' },
            ]
          },
          {
            title: 'Trị Liệu Chuyên Khoa',
            items: [
              { label: 'Trị Mụn Bio-Light Chuẩn FDA', query: 'booking' },
              { label: 'Cấy Glow Skin Căng Bóng', query: 'booking' },
              { label: 'Nâng Cơ Hifu Ultra V-Line', query: 'booking' },
              { label: 'Triệt Lông Diode Laser Không Đau', query: 'booking' },
            ]
          },
          {
            title: 'Đặc Quyền Đặt Hẹn',
            items: [
              { label: 'Bác Sĩ Da Liễu Khám Miễn Phí', query: 'booking' },
              { label: 'Soi Da 3D Đa Quang Phổ Visia', query: 'booking' },
              { label: 'Giữ Chỗ Phòng Khám Riêng Biệt', query: 'booking' },
              { label: 'Bảng Giá & Gói Liệu Trình', query: '#spa-section' },
            ]
          }
        ],
        featuredBrands: ['BeautyShop Clinic', 'CIDESCO Certified', 'FDA Approved']
      },
      brands_zone: {
        id: 'brands_zone',
        label: 'Thương Hiệu Hàng Đầu',
        icon: Crown,
        columns: [
          {
            title: 'Dược Mỹ Phẩm Châu Âu',
            items: [
              { label: 'La Roche-Posay', query: 'products?search=La+Roche-Posay' },
              { label: 'Bioderma', query: 'products?search=Bioderma' },
              { label: 'Vichy Laboratoires', query: 'products?search=Vichy' },
              { label: 'SVR Dermatologique', query: 'products?search=SVR' },
            ]
          },
          {
            title: 'Dược Mỹ Phẩm Mỹ & Úc',
            items: [
              { label: 'CeraVe Skincare', query: 'products?search=CeraVe' },
              { label: "Paula's Choice", query: "products?search=Paula's+Choice" },
              { label: 'SkinCeuticals', query: 'products?search=SkinCeuticals' },
              { label: 'Eucerin', query: 'products?search=Eucerin' },
            ]
          },
          {
            title: 'Mỹ Phẩm Hàn Quốc & Nhật',
            items: [
              { label: 'COSRX Chăm Sóc Da Mụn', query: 'products?search=COSRX' },
              { label: 'Anua Heartleaf Làm Dịu', query: 'products?search=Anua' },
              { label: 'Some By Mi Trị Mụn 30 Ngày', query: 'products?search=Some+By+Mi' },
              { label: 'Innisfree Trà Xanh Đảo Jeju', query: 'products?search=Innisfree' },
            ]
          }
        ],
        featuredBrands: ['Chính Hãng 100%', 'Đền 200% Hàng Giả', 'NowFree 2H']
      }
    };
  }, []);

  return (
    <header className="customer-header" ref={headerRef}>
      {/* 1. TOP UTILITY & ANNOUNCEMENT BAR */}
      <div className="customer-header-top">
        <div className="customer-header-container" style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '12px'
        }}>
          {/* Left Guarantees */}
          <div className="header-guarantees" style={{ display: 'flex', alignItems: 'center', gap: '14px', letterSpacing: '0.02em' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <ShieldCheck size={14} color="#FFB3C6" />
              100% Chính Hãng - Giá Trị Thật
            </span>
            <span style={{ opacity: 0.4 }}>•</span>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Truck size={14} color="#FFB3C6" />
              Giao Nhanh 2H NowFree
            </span>
            <span style={{ opacity: 0.4 }}>•</span>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <RotateCcw size={14} color="#FFB3C6" />
              Đổi Trả Miễn Phí 14 Ngày
            </span>
          </div>

          {/* Right Utilities */}
          <div className="header-utilities" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            <a href="tel:18006324" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <PhoneCall size={13} color="#FFB3C6" />
              <span>Hotline: <strong style={{ color: '#FFFFFF', marginLeft: '2px' }}>1800 6324</strong></span>
            </a>

            <span
              onClick={() => handleNavClick('#spa-section')}
              style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px' }}
              className="hover-underline"
            >
              <MapPin size={13} color="#FFB3C6" />
              <span>Hệ Thống Clinic & Spa</span>
            </span>

            <span
              onClick={() => handleNavClick('profile?tab=orders')}
              style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px' }}
              className="hover-underline"
            >
              <Package size={13} color="#FFB3C6" />
              <span>Tra cứu đơn hàng</span>
            </span>

            {/* Quick Admin Portal shortcut for Operators */}
            {isOperator && (
              <button
                onClick={() => { window.location.hash = '/dashboard'; }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '5px',
                  background: 'rgba(255, 255, 255, 0.2)',
                  border: '1px solid rgba(255, 255, 255, 0.4)',
                  color: '#FFFFFF',
                  padding: '2px 10px',
                  borderRadius: '9999px',
                  fontSize: '11px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                <LayoutDashboard size={12} />
                Quản Trị Hệ Thống
              </button>
            )}
          </div>
        </div>
      </div>

      {/* 2. MAIN HEADER ROW */}
      <div className="customer-header-main">
        <div className="customer-header-container customer-header-main-grid">

          {/* Brand Logo - Pastel Pink Style */}
          <div
            className="customer-brand-logo"
            onClick={() => handleNavClick('')}
            style={{ cursor: 'pointer', display: 'flex', flexDirection: 'column', flexShrink: 0 }}
          >
            <span style={{
              fontFamily: 'var(--font-sans)',
              fontSize: '26px',
              fontWeight: 900,
              letterSpacing: '0.04em',
              color: '#FFFFFF',
              lineHeight: 1
            }}>
              BEAUTY<span style={{ color: '#FFCCD5' }}>SHOP</span>
            </span>
            <span style={{
              fontSize: '8.5px',
              letterSpacing: '0.18em',
              color: 'rgba(255, 255, 255, 0.92)',
              textTransform: 'uppercase',
              marginTop: '4px',
              fontWeight: 700
            }}>
              Chất Lượng Thật • Giá Trị Thật
            </span>
          </div>

          {/* Category Mega Menu Button */}
          <div ref={categoryRef} style={{ position: 'relative' }}>
            <button
              className={`category-dropdown-btn ${categoryMenuOpen ? 'active' : ''}`}
              onClick={() => setCategoryMenuOpen(!categoryMenuOpen)}
            >
              <Layers size={16} />
              <span>Danh Mục Sản Phẩm</span>
              <ChevronDown
                size={13}
                style={{
                  transition: 'transform 0.2s ease',
                  transform: categoryMenuOpen ? 'rotate(180deg)' : 'rotate(0)'
                }}
              />
            </button>

            {/* Dynamic Mega Category Dropdown Popup - 3-Panel Hierarchical Structure */}
            {categoryMenuOpen && (
              <div className="category-mega-menu">
                {/* 1. Left Sidebar: Level 1 Categories */}
                <div className="category-mega-sidebar">
                  {Object.values(megaHierarchy).map((item) => {
                    const isHovered = hoveredLevel1 === item.id;
                    const ItemIcon = item.icon;
                    return (
                      <div
                        key={item.id}
                        className={`category-mega-sidebar-item ${isHovered ? 'active' : ''}`}
                        onMouseEnter={() => setHoveredLevel1(item.id)}
                        onClick={() => {
                          if (item.id === 'spa_clinic') handleNavClick('booking');
                          else handleNavClick(`products?filter=${item.id}`);
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <ItemIcon size={14} color={isHovered ? 'var(--c-primary)' : '#6B7280'} />
                          <span>{cleanDisplayName(item.label)}</span>
                        </div>
                        <ChevronRight size={13} color={isHovered ? 'var(--c-primary)' : '#9CA3AF'} />
                      </div>
                    );
                  })}
                </div>

                {/* 2. Center Panel: Level 2 & Level 3 Subcategories */}
                <div className="category-mega-content">
                  {megaHierarchy[hoveredLevel1]?.columns.map((col, idx) => (
                    <div key={idx}>
                      <div className="category-mega-col-title">
                        <Sparkles size={12} color="var(--c-primary)" />
                        <span>{cleanDisplayName(col.title)}</span>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        {col.items.map((sub, sIdx) => (
                          <div
                            key={sIdx}
                            className="category-mega-link"
                            onClick={() => handleNavClick(sub.query)}
                          >
                            <span>{cleanDisplayName(sub.label)}</span>
                            <ChevronRight size={11} color="#9CA3AF" />
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>

                {/* 3. Right Panel: Top Brands & Featured Deal Promo */}
                <div className="category-mega-banner-panel">
                  <div>
                    <div style={{
                      fontSize: '11px',
                      fontWeight: 800,
                      color: 'var(--c-primary)',
                      textTransform: 'uppercase',
                      letterSpacing: '0.06em',
                      marginBottom: '10px'
                    }}>
                      Thương Hiệu Nổi Bật
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      {megaHierarchy[hoveredLevel1]?.featuredBrands.map((bName, bIdx) => (
                        <div
                          key={bIdx}
                          onClick={() => handleNavClick(`products?search=${encodeURIComponent(bName)}`)}
                          style={{
                            padding: '6px 10px',
                            backgroundColor: 'var(--c-canvas, #FAF7F8)',
                            borderRadius: '4px',
                            border: '1px solid var(--c-border)',
                            fontSize: '12px',
                            fontWeight: 700,
                            color: '#1F2937',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            transition: 'all 0.15s ease'
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.borderColor = 'var(--c-primary)';
                            e.currentTarget.style.color = 'var(--c-primary)';
                            e.currentTarget.style.backgroundColor = 'var(--c-primary-light, #FFF0F3)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.borderColor = 'var(--c-border)';
                            e.currentTarget.style.color = '#1F2937';
                            e.currentTarget.style.backgroundColor = 'var(--c-canvas, #FAF7F8)';
                          }}
                        >
                          <span>{cleanDisplayName(bName)}</span>
                          <ArrowRight size={11} />
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Promo Mini Card */}
                  <div style={{
                    marginTop: '16px',
                    padding: '12px',
                    borderRadius: '6px',
                    backgroundColor: 'var(--c-primary-light, #FFF0F3)',
                    border: '1px solid var(--c-gold-border)'
                  }}>
                    <div style={{ fontSize: '10.5px', fontWeight: 800, color: 'var(--c-deal-red, #E11D48)' }}>
                      FLASH DEALS -50%
                    </div>
                    <div style={{ fontSize: '11.5px', fontWeight: 600, color: '#1F2937', marginTop: '2px' }}>
                      Giao nhanh 2H miễn phí
                    </div>
                    <button
                      onClick={() => handleNavClick('#flash-sale')}
                      style={{
                        marginTop: '8px',
                        width: '100%',
                        padding: '6px',
                        backgroundColor: 'var(--c-primary)',
                        color: '#FFFFFF',
                        border: 'none',
                        borderRadius: '3px',
                        fontSize: '11px',
                        fontWeight: 700,
                        cursor: 'pointer'
                      }}
                    >
                      Săn Ngay
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Expansive E-Commerce Search Bar */}
          <div ref={searchRef} className="ecommerce-search-hub">
            <form onSubmit={handleSearchSubmit}>
              <div className="ecommerce-search-input-wrap">
                <Search
                  size={15}
                  style={{
                    position: 'absolute',
                    left: '16px',
                    color: 'var(--c-text-light)'
                  }}
                />

                <input
                  type="text"
                  className="ecommerce-search-input"
                  placeholder="Tìm kiếm sản phẩm, thương hiệu hoặc hoạt chất..."
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  onFocus={() => setIsSearchFocused(true)}
                />

                {searchKeyword && (
                  <button
                    type="button"
                    onClick={() => setSearchKeyword('')}
                    style={{
                      position: 'absolute',
                      right: '80px',
                      background: 'none',
                      border: 'none',
                      color: 'var(--c-text-light)',
                      cursor: 'pointer',
                      padding: '4px'
                    }}
                  >
                    <X size={14} />
                  </button>
                )}

                <button type="submit" className="ecommerce-search-submit">
                  <span>Tìm kiếm</span>
                </button>
              </div>
            </form>

            {/* Dynamic Trending Keyword Tags below search bar */}
            <div className="ecommerce-trending-tags">
              <span style={{ color: 'var(--c-gold-hover)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '4px' }}>
                <Flame size={12} color="var(--c-gold-hover)" /> Xu hướng:
              </span>
              {popularKeywords.map((kw, idx) => (
                <span
                  key={idx}
                  className="ecommerce-trending-tag"
                  onClick={() => handleQuickKeyword(kw)}
                >
                  {cleanDisplayName(kw)}
                </span>
              ))}
            </div>

            {/* Search Suggestions Popup when Focused */}
            {isSearchFocused && (
              <div style={{
                position: 'absolute',
                top: 'calc(100% + 4px)',
                left: 0,
                right: 0,
                backgroundColor: '#FFFFFF',
                borderRadius: 'var(--radius-md)',
                boxShadow: '0 15px 35px rgba(0,0,0,0.12)',
                border: '1px solid var(--c-border)',
                padding: '16px',
                zIndex: 115,
                animation: 'fadeInDown 0.18s ease'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                  <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--c-gold-hover)', textTransform: 'uppercase' }}>
                    Từ khóa tìm kiếm thịnh hành
                  </span>
                  <span style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>Nhấn để tìm nhanh</span>
                </div>

                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginBottom: '14px' }}>
                  {popularKeywords.map((kw, idx) => (
                    <span
                      key={idx}
                      onClick={() => handleQuickKeyword(kw)}
                      style={{
                        padding: '6px 12px',
                        backgroundColor: 'var(--c-canvas)',
                        borderRadius: 'var(--radius-pill)',
                        fontSize: '12px',
                        color: 'var(--c-primary)',
                        cursor: 'pointer',
                        border: '1px solid var(--c-border-subtle)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                        transition: 'all 0.15s ease'
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.borderColor = 'var(--c-gold)';
                        e.currentTarget.style.color = 'var(--c-gold-hover)';
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.borderColor = 'var(--c-border-subtle)';
                        e.currentTarget.style.color = 'var(--c-primary)';
                      }}
                    >
                      <Search size={11} color="var(--c-text-light)" />
                      {cleanDisplayName(kw)}
                    </span>
                  ))}
                </div>

                <div style={{ borderTop: '1px solid var(--c-border-subtle)', paddingTop: '10px' }}>
                  <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--c-text-muted)', marginBottom: '8px' }}>
                    Gợi ý chuyên sâu từ Bác Sĩ & Chuyên Gia:
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px' }}>
                    {[
                      { title: 'Phác đồ phục hồi da treatment', target: '#routine-builder' },
                      { title: 'Kiểm tra độ an toàn thành phần EWG', target: '#inci-library' },
                      { title: 'Dịch vụ Spa Aqua Peel chuyên sâu', target: 'booking' },
                      { title: 'Săn ưu đãi Flash Sale -40%', target: '#flash-sale' },
                    ].map((sug, idx) => (
                      <div
                        key={idx}
                        onClick={() => {
                          setIsSearchFocused(false);
                          handleNavClick(sug.target);
                        }}
                        style={{
                          fontSize: '12px',
                          color: 'var(--c-primary)',
                          padding: '6px 8px',
                          borderRadius: '6px',
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px',
                          transition: 'background 0.15s ease'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--c-gold-light)'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                      >
                        <Sparkles size={12} color="var(--c-gold)" />
                        <span>{sug.title}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Right Action Hub */}
          <div className="header-action-hub" style={{ display: 'flex', alignItems: 'center', gap: '12px', flexShrink: 0 }}>

            {/* Đặt Lịch Spa CTA Button - Hasaki Style */}
            <button
              onClick={() => handleNavClick('booking')}
              className="btn-luxury-gold btn-header-booking"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                background: 'rgba(255, 255, 255, 0.15)',
                border: '1px solid rgba(255, 255, 255, 0.35)',
                color: '#FFFFFF'
              }}
            >
              <Calendar size={14} />
              <span>Đặt Hẹn Clinic & Spa</span>
            </button>

            {/* Cart Button */}
            <button
              onClick={() => setCartOpen(true)}
              className="nav-action-item header-cart-button"
              title="Xem giỏ hàng"
            >
              <ShoppingBag size={18} color="#FFFFFF" />
              <div className="nav-cart-copy" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', lineHeight: 1.15 }}>
                <span style={{ fontSize: '10px', color: 'rgba(255, 255, 255, 0.85)', fontWeight: 500 }}>Giỏ hàng</span>
                <span style={{ fontSize: '13px', fontWeight: 700, color: '#FFFFFF' }}>
                  {totalItems > 0 ? `${totalItems} món` : 'Trống'}
                </span>
              </div>

              {totalItems > 0 && (
                <span className="nav-badge-pill">{totalItems}</span>
              )}
            </button>

            {/* User Account / Auth Button */}
            <div ref={userMenuRef} className="header-account-area" style={{ position: 'relative' }}>
              {isAuthenticated ? (
                <div>
                  <button
                    onClick={() => setUserMenuOpen(!userMenuOpen)}
                    className="nav-action-item"
                    style={{
                      background: 'rgba(255, 255, 255, 0.15)',
                      borderColor: 'rgba(255, 255, 255, 0.3)'
                    }}
                  >
                    <div style={{
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      backgroundColor: '#FFFFFF',
                      color: 'var(--c-primary)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '11px',
                      fontWeight: 800
                    }}>
                      {(user?.fullName || user?.username || 'U').charAt(0).toUpperCase()}
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', lineHeight: 1.15 }}>
                      <span style={{ fontSize: '10px', color: '#FFCCD5', fontWeight: 600 }}>Tài khoản</span>
                      <span style={{ fontSize: '13px', fontWeight: 700, color: '#FFFFFF' }}>
                        {user?.fullName?.split(' ').pop() || user?.username || 'Bạn'}
                      </span>
                    </div>
                    <ChevronDown size={12} color="rgba(255, 255, 255, 0.85)" />
                  </button>

                  {userMenuOpen && (
                    <div style={{
                      position: 'absolute',
                      right: 0,
                      top: 'calc(100% + 8px)',
                      width: '230px',
                      background: '#FFFFFF',
                      border: '1px solid var(--c-border)',
                      borderRadius: 'var(--radius-md)',
                      boxShadow: '0 15px 35px rgba(0,0,0,0.12)',
                      padding: '8px 0',
                      zIndex: 120,
                      animation: 'fadeInDown 0.18s ease'
                    }}>
                      <div style={{ padding: '10px 16px', borderBottom: '1px solid var(--c-border-subtle)' }}>
                        <div style={{ fontSize: '13.5px', fontWeight: 700, color: 'var(--c-text-main)' }}>{user?.fullName || user?.username}</div>
                        <div style={{ fontSize: '11px', color: 'var(--c-text-muted)' }}>{user?.email}</div>
                      </div>

                      <div
                        onClick={() => { handleNavClick('profile'); setUserMenuOpen(false); }}
                        style={{ padding: '10px 16px', fontSize: '13px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '9px', color: 'var(--c-text-main)' }}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--c-primary-light)'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                      >
                        <User size={15} color="var(--c-primary)" />
                        <span>Ví Thẻ Spa & Hồ Sơ Da</span>
                      </div>

                      <div
                        onClick={() => { handleNavClick('profile?tab=orders'); setUserMenuOpen(false); }}
                        style={{ padding: '10px 16px', fontSize: '13px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '9px', color: 'var(--c-text-main)' }}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--c-primary-light)'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                      >
                        <Package size={15} color="var(--c-primary)" />
                        <span>Đơn Hàng Của Tôi</span>
                      </div>

                      {isOperator && (
                        <div
                          onClick={() => { window.location.hash = '/dashboard'; }}
                          style={{ padding: '10px 16px', fontSize: '13px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '9px', color: 'var(--c-primary)', fontWeight: 600 }}
                          onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--c-primary-light)'}
                          onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                        >
                          <LayoutDashboard size={14} />
                          <span>Vào Quản Trị Hệ Thống</span>
                        </div>
                      )}

                      <div
                        onClick={() => { logout(); setUserMenuOpen(false); }}
                        style={{ padding: '10px 16px', fontSize: '13px', cursor: 'pointer', borderTop: '1px solid var(--c-border-subtle)', color: 'var(--c-danger-red)', display: 'flex', alignItems: 'center', gap: '9px' }}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#FEF2F2'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                      >
                        <LogOut size={15} />
                        <span>Đăng xuất</span>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <button
                  onClick={() => handleNavClick('login')}
                  className="nav-action-item"
                  style={{
                    background: '#FFFFFF',
                    color: 'var(--c-primary)',
                    borderColor: '#FFFFFF',
                    fontWeight: 700
                  }}
                >
                  <User size={15} color="var(--c-primary)" />
                  <span>Đăng nhập</span>
                </button>
              )}
            </div>

            {/* Mobile Hamburger Menu Toggle */}
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'transparent',
                border: 'none',
                cursor: 'pointer',
                padding: '6px',
                color: 'var(--c-primary)'
              }}
              className="mobile-nav-toggle"
              aria-label="Menu"
            >
              {mobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
            </button>
          </div>
        </div>
      </div>

      {/* 3. HORIZONTAL SUB-NAVIGATION MENU BAR WITH 8 UX/UI TABS */}
      <nav className="customer-subnav-bar">
        <div className="customer-header-container">
          <div className="customer-subnav-links">
            {subnavItems.map((link) => {
              const active = currentRoute === link.id || (link.id === 'home' && (currentRoute === '' || currentRoute === 'home'));
              const isDropdownOpen = activeDropdownTab === link.id;
              const LinkIcon = link.Icon;

              return (
                <div
                  key={link.id}
                  className="customer-subnav-dropdown-wrapper"
                  onMouseEnter={() => {
                    if (link.hasDropdown) {
                      clearTimeout(subnavDropdownTimer.current);
                      setActiveDropdownTab(link.id);
                    }
                  }}
                  onMouseLeave={() => {
                    if (link.hasDropdown) {
                      subnavDropdownTimer.current = setTimeout(() => {
                        setActiveDropdownTab(null);
                      }, 200);
                    }
                  }}
                >
                  <div
                    onClick={() => handleNavClick(link.route)}
                    className={`customer-subnav-item nav-item-${link.id} ${active ? 'active' : ''}`}
                    style={{ color: link.color || undefined, display: 'flex', alignItems: 'center', gap: '7px' }}
                  >
                    <LinkIcon size={14} style={{ color: link.color || 'var(--c-gold)' }} />
                    <span>{link.label}</span>
                    {link.hasDropdown && (
                      <ChevronDown
                        size={12}
                        style={{
                          opacity: 0.7,
                          transform: isDropdownOpen ? 'rotate(180deg)' : 'rotate(0)',
                          transition: 'transform 0.15s ease'
                        }}
                      />
                    )}
                    {link.highlightBadge && (
                      <span style={{
                        backgroundColor: link.highlightBadge === 'VIP' ? 'var(--c-gold)' : '#DC2626',
                        color: '#FFFFFF',
                        fontSize: '9.5px',
                        fontWeight: 800,
                        padding: '1px 6px',
                        borderRadius: '9999px',
                        letterSpacing: '0.02em'
                      }}>
                        {link.highlightBadge}
                      </span>
                    )}
                  </div>

                  {/* Subnav Flyout Dropdown for Categories, Brands, or Spa */}
                  {link.hasDropdown && isDropdownOpen && (
                    <div
                      className="customer-subnav-dropdown"
                      onMouseEnter={() => clearTimeout(subnavDropdownTimer.current)}
                      onMouseLeave={() => {
                        subnavDropdownTimer.current = setTimeout(() => {
                          setActiveDropdownTab(null);
                        }, 150);
                      }}
                      style={{ minWidth: link.dropdownType === 'categories' ? '460px' : link.dropdownType === 'brands' ? '320px' : '280px' }}
                    >
                      <div style={{ padding: '8px 16px', borderBottom: '1px solid var(--c-border-subtle)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{ fontSize: '11px', fontWeight: 800, color: 'var(--c-primary)', textTransform: 'uppercase' }}>
                          {link.label}
                        </span>
                        <span
                          onClick={() => handleNavClick(link.route)}
                          style={{ fontSize: '11px', color: 'var(--c-primary)', fontWeight: 700, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '3px' }}
                        >
                          <span>Xem tất cả</span>
                          <ArrowRight size={11} />
                        </span>
                      </div>

                      {/* Content based on dropdownType */}
                      {link.dropdownType === 'categories' && (
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', padding: '10px 12px', maxHeight: '340px', overflowY: 'auto' }}>
                          <div>
                            <div style={{ fontSize: '11px', fontWeight: 800, color: 'var(--c-primary)', padding: '4px 8px 6px', borderBottom: '1.5px solid var(--c-primary-light, #FFF0F3)', marginBottom: '4px' }}>
                              LÀM SẠCH & CÂN BẰNG
                            </div>
                            {[
                              { label: 'Sữa Rửa Mặt Y Khoa', q: 'products?search=sữa+rửa+mặt' },
                              { label: 'Nước Tẩy Trang Micellar', q: 'products?search=tẩy+trang' },
                              { label: 'Dầu Tẩy Trang Nhũ Hóa', q: 'products?search=dầu+tẩy+trang' },
                              { label: 'Nước Hoa Hồng / Toner', q: 'products?search=toner' },
                              { label: 'Tẩy Tế Bào Chết Mặt', q: 'products?search=tẩy+tế+bào+chết' },
                            ].map((sub, sIdx) => (
                              <div
                                key={sIdx}
                                className="customer-subnav-dropdown-item"
                                style={{ padding: '6px 8px', fontSize: '12px' }}
                                onClick={() => handleNavClick(sub.q)}
                              >
                                <span>{cleanDisplayName(sub.label)}</span>
                                <ChevronRight size={11} color="var(--c-primary)" />
                              </div>
                            ))}
                          </div>

                          <div>
                            <div style={{ fontSize: '11px', fontWeight: 800, color: 'var(--c-primary)', padding: '4px 8px 6px', borderBottom: '1.5px solid var(--c-primary-light, #FFF0F3)', marginBottom: '4px' }}>
                              ĐẶC TRỊ & DƯỠNG ẨM
                            </div>
                            {[
                              { label: 'Serum B5 Phục Hồi Màng Da', q: 'products?search=serum+b5' },
                              { label: 'Kem Chống Nắng Phổ Rộng', q: 'products?search=chống+nắng' },
                              { label: 'Kem Dưỡng Ẩm Màng Da', q: 'products?search=kem+dưỡng' },
                              { label: 'Tinh Chất BHA 2% Trị Mụn', q: 'products?search=bha' },
                              { label: 'Retinol Vi Nang Chống Lão Hóa', q: 'products?search=retinol' },
                            ].map((sub, sIdx) => (
                              <div
                                key={sIdx}
                                className="customer-subnav-dropdown-item"
                                style={{ padding: '6px 8px', fontSize: '12px' }}
                                onClick={() => handleNavClick(sub.q)}
                              >
                                <span>{cleanDisplayName(sub.label)}</span>
                                <ChevronRight size={11} color="var(--c-primary)" />
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {link.dropdownType === 'brands' && (
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4px', padding: '8px', maxHeight: '280px', overflowY: 'auto' }}>
                          {topBrands.slice(0, 10).map((brand) => (
                            <div
                              key={brand.id}
                              className="customer-subnav-dropdown-item"
                              style={{ padding: '7px 10px', fontSize: '12.5px', borderRadius: '4px' }}
                              onClick={() => handleNavClick(`products?brandId=${brand.id}&brand=${brand.slug}&brandName=${encodeURIComponent(cleanDisplayName(brand.name))}`)}
                            >
                              <span>{cleanDisplayName(brand.name)}</span>
                              <ChevronRight size={11} color="var(--c-primary)" />
                            </div>
                          ))}
                        </div>
                      )}

                      {link.dropdownType === 'spa' && (
                        <div style={{ padding: '8px', maxHeight: '280px', overflowY: 'auto' }}>
                          {spaServices.slice(0, 6).map((service) => (
                            <div
                              key={service.id}
                              className="customer-subnav-dropdown-item"
                              style={{ padding: '8px 12px', fontSize: '12.5px', borderRadius: '4px' }}
                              onClick={() => handleNavClick(`booking?serviceId=${service.id}`)}
                            >
                              <div>
                                <div style={{ fontWeight: 600 }}>{cleanDisplayName(service.name)}</div>
                                <div style={{ fontSize: '11px', color: 'var(--c-text-light)' }}>{service.durationMinutes || 60} phút • Chuẩn FDA</div>
                              </div>
                              <ChevronRight size={12} color="var(--c-primary)" />
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </nav>

      {/* 4. MOBILE SLIDING DRAWER MENU */}
      {mobileMenuOpen && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.55)',
          backdropFilter: 'blur(4px)',
          zIndex: 200,
          display: 'flex',
          justifyContent: 'flex-end',
          animation: 'fadeIn 0.2s ease'
        }}>
          <div style={{
            width: '85%',
            maxWidth: '380px',
            height: '100%',
            backgroundColor: '#FFFFFF',
            boxShadow: '-10px 0 30px rgba(0, 0, 0, 0.2)',
            display: 'flex',
            flexDirection: 'column',
            overflowY: 'auto',
            padding: '24px 20px',
            animation: 'slideInRight 0.25s cubic-bezier(0.16, 1, 0.3, 1)'
          }}>
            {/* Drawer Top Header - Hasaki Style */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <div>
                <span style={{ fontFamily: 'var(--font-sans)', fontSize: '22px', fontWeight: 900, color: 'var(--c-primary)' }}>
                  BEAUTY<span style={{ color: 'var(--c-deal-red, #E31837)' }}>SHOP</span>
                </span>
                <div style={{ fontSize: '10px', color: 'var(--c-primary)', fontWeight: 700, letterSpacing: '0.08em' }}>Chất Lượng Thật • Giá Trị Thật</div>
              </div>
              <button
                onClick={() => setMobileMenuOpen(false)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '6px' }}
              >
                <X size={22} color="var(--c-primary)" />
              </button>
            </div>

            {/* Mobile Search Input */}
            <form onSubmit={handleSearchSubmit} style={{ marginBottom: '20px' }}>
              <div style={{
                position: 'relative',
                display: 'flex',
                alignItems: 'center'
              }}>
                <input
                  type="text"
                  placeholder="Tìm kiếm sản phẩm, hoạt chất..."
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 36px 10px 14px',
                    borderRadius: 'var(--radius-pill)',
                    border: '1.5px solid var(--c-border)',
                    fontSize: '13px',
                    outline: 'none'
                  }}
                />
                <button type="submit" style={{ position: 'absolute', right: '10px', background: 'none', border: 'none', color: 'var(--c-primary)', cursor: 'pointer' }}>
                  <Search size={16} />
                </button>
              </div>
            </form>

            {/* Mobile Nav Links */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', flex: 1 }}>
              {subnavItems.map((link) => {
                const LinkIcon = link.Icon;
                return (
                  <div key={link.id}>
                    <div
                      onClick={() => handleNavClick(link.route)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '12px 14px',
                        borderRadius: '8px',
                        fontSize: '14px',
                        fontWeight: 600,
                        color: 'var(--c-primary)',
                        cursor: 'pointer'
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--c-canvas)'}
                      onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <LinkIcon size={16} style={{ color: link.color || 'var(--c-gold)' }} />
                        <span>{link.label}</span>
                      </div>
                      {link.highlightBadge && (
                        <span style={{
                          backgroundColor: '#DC2626',
                          color: '#FFFFFF',
                          fontSize: '10px',
                          fontWeight: 800,
                          padding: '2px 7px',
                          borderRadius: '9999px'
                        }}>
                          {link.highlightBadge}
                        </span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Mobile Footer CTAs */}
            <div style={{ paddingTop: '20px', borderTop: '1px solid var(--c-border-subtle)', display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <button
                onClick={() => handleNavClick('booking')}
                className="btn-luxury-gold"
                style={{ width: '100%', padding: '12px', fontSize: '13px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
              >
                <Calendar size={16} />
                <span>Đặt Lịch Hẹn Spa Ngay</span>
              </button>

              <div style={{ textAlign: 'center', fontSize: '12px', color: 'var(--c-text-muted)', marginTop: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}>
                <Phone size={14} color="var(--c-gold)" />
                <span>Hotline hỗ trợ: <strong>1900 8888</strong></span>
              </div>
            </div>
          </div>
        </div>
      )}
    </header>
  );
};
export default CustomerHeader;
