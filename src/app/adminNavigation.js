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
    label: "Không gian làm việc",
    items: [
      {
        id: "dashboard",
        label: "Tổng quan",
        description: "Báo cáo & hiệu quả kinh doanh",
        icon: LayoutDashboard,
        keywords: "dashboard doanh thu báo cáo overview",
      },
    ],
  },
  {
    label: "Kinh doanh",
    items: [
      {
        id: "orders",
        label: "Đơn hàng",
        description: "Theo dõi & xử lý đơn hàng",
        icon: ShoppingBag,
        role: "ROLE_ORDER_STAFF",
        keywords: "order đơn hàng giao vận",
      },
      {
        id: "products",
        label: "Sản phẩm",
        description: "Danh mục, thương hiệu, biến thể & thuộc tính",
        icon: Package,
        role: "ROLE_CATALOG_STAFF",
        keywords:
          "catalog sku mỹ phẩm danh mục thương hiệu thuộc tính attribute tag thẻ nhãn hoạt chất thành phần da liễu",
      },
      {
        id: "inventory",
        label: "Kho & tồn kho",
        description: "Quản lý kho, lô hàng & kiểm kê",
        icon: Boxes,
        role: "ROLE_INVENTORY_STAFF",
        keywords: "stock inventory kho kiểm kê",
      },
      {
        id: "procurement",
        label: "Mua hàng",
        description: "Nhà cung cấp & phiếu nhập",
        icon: ClipboardList,
        role: "ROLE_INVENTORY_STAFF",
        keywords: "supplier procurement nhà cung cấp nhập hàng",
      },
    ],
  },
  {
    label: "Trải nghiệm khách hàng",
    items: [
      {
        id: "spa-reception",
        label: "Lễ tân & Quầy Spa",
        description: "Tiếp đón, check-in, phân ca KTV & thu ngân tại quầy",
        icon: UserCheck,
        role: ["ROLE_STAFF", "ROLE_SPA_RECEPTION", "ROLE_ADMIN"],
        keywords:
          "reception lễ tân tiếp đón check-in thu ngân thanh toán quầy spa lịch hẹn đặt lịch",
      },
      {
        id: "spa",
        label: "Spa & lịch hẹn",
        description: "Lịch hẹn, liệu trình, tài nguyên & nhân sự",
        icon: CalendarDays,
        role: ["ROLE_STAFF", "ROLE_SPA_RECEPTION", "ROLE_SPA_THERAPIST"],
        keywords:
          "spa appointment dịch vụ lịch hẹn nhân viên kỹ thuật viên buồng phòng giường máy móc bảo trì khảo sát an toàn vé liệu trình",
      },
      {
        id: "crm",
        label: "Khách hàng",
        description: "Hồ sơ & chăm sóc khách hàng",
        icon: HeartHandshake,
        role: "ROLE_CS_STAFF",
        keywords: "crm customer hồ sơ chăm sóc khách",
      },
      {
        id: "vouchers",
        label: "Khuyến mãi",
        description: "Ưu đãi & mã giảm giá",
        icon: TicketPercent,
        keywords: "voucher promotion giảm giá ưu đãi",
      },
      {
        id: "reviews",
        label: "Đánh giá",
        description: "Kiểm duyệt & phản hồi đánh giá",
        icon: MessageSquareText,
        role: "ROLE_CS_STAFF",
        keywords: "review nhận xét phản hồi kiểm duyệt",
      },
    ],
  },
  {
    label: "Quản trị",
    items: [
      {
        id: "users",
        label: "Người dùng",
        description: "Tài khoản & phân quyền",
        icon: UsersRound,
        keywords: "user account tài khoản phân quyền bảo mật",
      },
      {
        id: "operations",
        label: "Vận hành hệ thống",
        description: "Tài chính, kiểm toán, cấu hình & AI Chatbot",
        icon: SlidersHorizontal,
        keywords:
          "system audit payment tài chính thanh toán cấu hình kiểm toán chatbot ai rag vector qdrant nlu",
      },
    ],
  },
];

export const getAdminSections = (roles = []) => {
  const isAdmin = roles.includes("ROLE_ADMIN") || roles.includes("ADMIN");
  return ADMIN_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter(
      (item) =>
        isAdmin ||
        (item.role &&
          (Array.isArray(item.role)
            ? item.role.some((r) => roles.includes(r))
            : roles.includes(item.role))),
    ),
  })).filter((section) => section.items.length);
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
