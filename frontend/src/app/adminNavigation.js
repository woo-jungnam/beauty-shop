import {
  LayoutDashboard,
  ShoppingBag,
  Boxes,
  Package,
  CalendarDays,
  TicketPercent,
  MessageSquareText,
  UsersRound,
  ClipboardList,
  HeartHandshake,
  SlidersHorizontal,
  UserCheck,
} from "lucide-react";

export const ADMIN_SECTIONS = [
  {
    label: "Báo cáo & Tổng quan",
    items: [
      {
        id: "dashboard",
        label: "Tổng quan",
        description: "Báo cáo doanh thu & hiệu quả kinh doanh",
        icon: LayoutDashboard,
        role: "ROLE_STAFF",
        keywords: "dashboard doanh thu báo cáo overview tổng quan",
      },
    ],
  },
  {
    label: "Bán hàng & Kho vận",
    items: [
      {
        id: "orders",
        label: "Đơn hàng bán",
        description: "Theo dõi & xử lý đơn hàng",
        icon: ShoppingBag,
        role: "ROLE_STAFF",
        keywords: "order đơn hàng giao vận bán hàng",
      },
      {
        id: "products",
        label: "Danh mục sản phẩm",
        description: "Sản phẩm, phân loại mẫu mã & đặc tính",
        icon: Package,
        role: "ROLE_STAFF",
        keywords:
          "catalog sku mỹ phẩm danh mục thương hiệu thuộc tính attribute tag thẻ nhãn hoạt chất thành phần da liễu",
      },
      {
        id: "inventory",
        label: "Tồn kho & Kiểm kê",
        description: "Quản lý tồn kho, lô hàng & kiểm kê",
        icon: Boxes,
        role: "ROLE_STAFF",
        keywords: "stock inventory kho kiểm kê tồn kho",
      },
      {
        id: "procurement",
        label: "Nhập hàng nhà cung cấp",
        description: "Nhà cung cấp & phiếu nhập kho",
        icon: ClipboardList,
        role: "ROLE_STAFF",
        keywords: "supplier procurement nhà cung cấp nhập hàng mua hàng po",
      },
    ],
  },
  {
    label: "Dịch vụ Spa & Khách hàng",
    items: [
      {
        id: "spa-reception",
        label: "Lễ tân & Thu ngân quầy",
        description: "Tiếp đón, check-in, chỉ định KTV & thu ngân tại quầy",
        icon: UserCheck,
        role: "ROLE_STAFF",
        keywords:
          "reception lễ tân tiếp đón check-in thu ngân thanh toán quầy spa lịch hẹn đặt lịch",
      },
      {
        id: "spa",
        label: "Lịch hẹn & Kỹ thuật viên",
        description: "Lịch hẹn, liệu trình, buồng phòng & nhân sự",
        icon: CalendarDays,
        role: "ROLE_STAFF",
        keywords:
          "spa appointment dịch vụ lịch hẹn nhân viên kỹ thuật viên buồng phòng giường máy móc bảo trì khảo sát an toàn vé liệu trình",
      },
      {
        id: "crm",
        label: "Hồ sơ khách hàng",
        description: "Thông tin, lịch sử dịch vụ & chăm sóc khách",
        icon: HeartHandshake,
        role: "ROLE_STAFF",
        keywords: "crm customer hồ sơ chăm sóc khách khách hàng",
      },
      {
        id: 'treatments',
        label: 'Liệu trình chuyên sâu',
        description: 'Hồ sơ buổi điều trị, tiến độ & ảnh lâm sàng',
        icon: ClipboardList,
        role: 'ROLE_STAFF',
        keywords: 'treatment lộ trình điều trị buổi tiến độ tiếp nhận phiên bản gia hạn hoàn tiền',
      },
      {
        id: "vouchers",
        label: "Mã giảm giá & Ưu đãi",
        description: "Chương trình khuyến mãi & mã ưu đãi",
        icon: TicketPercent,
        role: "ROLE_STAFF",
        keywords: "voucher promotion giảm giá ưu đãi khuyến mãi",
      },
      {
        id: "reviews",
        label: "Đánh giá của khách",
        description: "Kiểm duyệt & phản hồi đánh giá",
        icon: MessageSquareText,
        role: "ROLE_STAFF",
        keywords: "review nhận xét phản hồi kiểm duyệt đánh giá",
      },
    ],
  },
  {
    label: "Hệ thống & Cài đặt",
    items: [
      {
        id: "users",
        label: "Nhân viên & Phân quyền",
        description: "Tài khoản nhân sự & phân quyền nghiệp vụ",
        icon: UsersRound,
        keywords: "user account tài khoản phân quyền bảo mật nhân viên",
      },
      {
        id: "operations",
        label: "Lịch sử thao tác & Cấu hình",
        description: "Tài chính, nhật ký thao tác & trợ lý AI",
        icon: SlidersHorizontal,
        keywords:
          "system audit payment tài chính thanh toán cấu hình kiểm toán chatbot ai rag vector qdrant nlu thao tác",
      },
    ],
  },
];

const TAB_PERMISSIONS = { dashboard: 'REPORTS', orders: 'ORDERS', products: 'CATALOG', inventory: 'INVENTORY', procurement: 'PROCUREMENT',
  'spa-reception': 'SPA_RECEPTION', spa: 'SPA_RECEPTION', treatments: ['SPA_RECEPTION', 'SPA_EXECUTION'], crm: 'CRM', vouchers: 'CATALOG', reviews: 'CATALOG' };

export const getAdminSections = (roles = [], permissions = []) => {
  const isAdmin = roles.includes("ROLE_ADMIN") || roles.includes("ADMIN");
  const isStaff = roles.includes("ROLE_STAFF") || roles.includes("STAFF");
  const permList = Array.isArray(permissions) ? permissions : [];
  return ADMIN_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter((item) => {
      if (isAdmin) return true;
      if (!isStaff) return false;
      if (!item.role) return false;
      if (permList.length === 0) return true;
      const required = TAB_PERMISSIONS[item.id];
      if (!required) return true;
      return Array.isArray(required)
        ? required.some((p) => permList.includes(p))
        : permList.includes(required);
    }),
  })).filter((section) => section.items.length > 0);
};

export const normalizeSearch = (value = "") =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
export const getInitials = (name = "") =>
  name
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((part) => part[0])
    .join("")
    .toUpperCase() || "BS";
