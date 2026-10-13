import React, { useState, useEffect, useRef } from 'react';
import {
  Package,
  Plus,
  Edit,
  Trash2,
  Image,
  FolderTree,
  Award,
  Sparkles,
  Sliders,
  Upload,
  Info,
  Tag as TagIcon,
  BookOpen,
  RefreshCw,
} from 'lucide-react';
import { apiClient, uploadFile } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import {
  formatCurrency,
  resolveMediaUrl,
} from '../shared/utils/formatters';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Badge } from '../shared/ui/Badge';
import { DataTable } from '../shared/ui/DataTable';
import { Modal } from '../shared/ui/Modal';
import { ProductFeaturesModal } from './ProductFeaturesModal';
import { lines, ingredientCatalog } from './productFeatures.js';

const QUICK_SAMPLE_IMAGES = [
  { label: 'Chống nắng LRP', url: 'https://images.unsplash.com/photo-1552046122-03184de85e08?auto=format&fit=crop&w=800&q=85' },
  { label: 'Sữa rửa mặt CeraVe', url: 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=800&q=85' },
  { label: 'Serum Hyalu B5', url: 'https://images.unsplash.com/photo-1620916566398-39f1143ab7be?auto=format&fit=crop&w=800&q=85' },
  { label: 'Toner Anua', url: 'https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?auto=format&fit=crop&w=800&q=85' },
  { label: 'Kem dưỡng ẩm', url: 'https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=800&q=85' },
];

const slugify = (value) => value.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

export const ProductsPage = () => {
  const [activeTab, setActiveTab] = useState('products');
  const [loading, setLoading] = useState(false);

  // ==========================================
  // TAB 1: PRODUCTS & VARIANTS
  // ==========================================
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Product Create/Edit Modal
  const [productModalOpen, setProductModalOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [submittingProduct, setSubmittingProduct] = useState(false);
  const [productFormError, setProductFormError] = useState('');

  // Form Fields
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [shortDescription, setShortDescription] = useState('');
  const [description, setDescription] = useState('');
  const [initialVariantPrice, setInitialVariantPrice] = useState('250000');
  const [initialVariantVolume, setInitialVariantVolume] = useState('50ml');
  const [categoryId, setCategoryId] = useState('');
  const [brandId, setBrandId] = useState('');
  const [isPublished, setIsPublished] = useState(true);
  const [productType, setProductType] = useState('PRODUCT');
  const [targetGender, setTargetGender] = useState('UNISEX');
  const [skinType, setSkinType] = useState('ALL_SKIN');
  const [ingredientsText, setIngredientsText] = useState('');
  const [featureProduct, setFeatureProduct] = useState(null);
  const [hasFragrance, setHasFragrance] = useState(false);
  const [hasAlcohol, setHasAlcohol] = useState(false);
  const [keyActivesSummary, setKeyActivesSummary] = useState('');
  const [productDetailLoading, setProductDetailLoading] = useState(false);
  const [howToUse, setHowToUse] = useState('');
  const [originCountry, setOriginCountry] = useState('');
  const [productVolume, setProductVolume] = useState('');
  const [isFeatured, setIsFeatured] = useState(false);
  const [uploadedUrl, setUploadedUrl] = useState('');
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef(null);

  // Variants Modal
  const [variantsModalOpen, setVariantsModalOpen] = useState(false);
  const [selectedProductForVariants, setSelectedProductForVariants] = useState(null);
  const [variantList, setVariantList] = useState([]);
  const [variantModalOpen, setVariantModalOpen] = useState(false);
  const [editingVariant, setEditingVariant] = useState(null);
  const [varSku, setVarSku] = useState('');
  const [varName, setVarName] = useState('');
  const [varPrice, setVarPrice] = useState('');
  const [varDiscountPrice, setVarDiscountPrice] = useState('');
  const [varVolume, setVarVolume] = useState('');
  const [varColor, setVarColor] = useState('');
  const [varBarcode, setVarBarcode] = useState('');

  // ==========================================
  // TAB 2: CATEGORIES
  // ==========================================
  const [categoryModalOpen, setCategoryModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [catName, setCatName] = useState('');
  const [catSlug, setCatSlug] = useState('');
  const [catDesc, setCatDesc] = useState('');
  const [catParentId, setCatParentId] = useState('');
  const [catOrder, setCatOrder] = useState('0');
  const [catSubmitting, setCatSubmitting] = useState(false);

  // ==========================================
  // TAB 3: BRANDS
  // ==========================================
  const [brandModalOpen, setBrandModalOpen] = useState(false);
  const [editingBrand, setEditingBrand] = useState(null);
  const [brandName, setBrandName] = useState('');
  const [brandSlug, setBrandSlug] = useState('');
  const [brandCountry, setBrandCountry] = useState('');
  const [brandLogo, setBrandLogo] = useState('');
  const [brandDesc, setBrandDesc] = useState('');
  const [brandSubmitting, setBrandSubmitting] = useState(false);

  // ==========================================
  // TAB 4: INGREDIENTS
  // ==========================================
  const [ingredients, setIngredients] = useState([]);
  const [ingModalOpen, setIngModalOpen] = useState(false);
  const [editingIngredient, setEditingIngredient] = useState(null);
  const [ingName, setIngName] = useState('');
  const [ingInci, setIngInci] = useState('');
  const [ingSlug, setIngSlug] = useState('');
  const [ingEwg, setIngEwg] = useState('1');
  const [ingActive, setIngActive] = useState(false);
  const [ingDesc, setIngDesc] = useState('');
  const [ingFunctions, setIngFunctions] = useState('');
  const [ingBenefits, setIngBenefits] = useState('');
  const [ingConcerns, setIngConcerns] = useState('');
  const [ingSubmitting, setIngSubmitting] = useState(false);

  // ==========================================
  // TAB 5: ATTRIBUTES
  // ==========================================
  const [attributes, setAttributes] = useState([]);
  const [attrModalOpen, setAttrModalOpen] = useState(false);
  const [editingAttr, setEditingAttr] = useState(null);
  const [attrName, setAttrName] = useState('');
  const [attrDataType, setAttrDataType] = useState('STRING');
  const [attrDesc, setAttrDesc] = useState('');
  const [attrSubmitting, setAttrSubmitting] = useState(false);

  // ==========================================
  // TAB 6: TAGS
  // ==========================================
  const [tags, setTags] = useState([]);
  const [tagModalOpen, setTagModalOpen] = useState(false);
  const [editingTag, setEditingTag] = useState(null);
  const [tagName, setTagName] = useState('');
  const [tagSubmitting, setTagSubmitting] = useState(false);

  // ==========================================
  // TAB 7: BANNERS
  // ==========================================
  const [banners, setBanners] = useState([]);
  const [bannerModalOpen, setBannerModalOpen] = useState(false);
  const [editingBanner, setEditingBanner] = useState(null);
  const [bannerForm, setBannerForm] = useState({
    title: '',
    badge: '',
    description: '',
    imageUrl: '',
    targetUrl: '',
    ctaText: '',
    position: 'HERO_SLIDE',
    sortOrder: 1,
    isActive: true,
  });
  const [bannerSubmitting, setBannerSubmitting] = useState(false);

  // ==========================================
  // PRODUCT IMAGES MODAL
  // ==========================================
  const [imagesModalOpen, setImagesModalOpen] = useState(false);
  const [selectedProductForImages, setSelectedProductForImages] = useState(null);
  const [imageList, setImageList] = useState([]);
  const [imageLoading, setImageLoading] = useState(false);
  const [newImageUrl, setNewImageUrl] = useState('');
  const [newImageAlt, setNewImageAlt] = useState('');
  const [newImagePrimary, setNewImagePrimary] = useState(false);
  const [newImageOrder, setNewImageOrder] = useState('0');
  const [imageSubmitting, setImageSubmitting] = useState(false);

  // ==========================================
  // DERMATOLOGY & ROUTINE MODAL
  // ==========================================
  const [dermaModalOpen, setDermaModalOpen] = useState(false);
  const [selectedProductForDerma, setSelectedProductForDerma] = useState(null);
  const [dermaWhenToUse, setDermaWhenToUse] = useState('');
  const [dermaFrequency, setDermaFrequency] = useState('1-2 lần/ngày');
  const [dermaInstructions, setDermaInstructions] = useState('');
  const [dermaWarnings, setDermaWarnings] = useState('');
  const [dermaSubmitting, setDermaSubmitting] = useState(false);

  // ==========================================
  // DATA FETCHING
  // ==========================================
  const fetchCatalogs = async () => {
    try {
      const [catRes, brandRes] = await Promise.allSettled([
        apiClient.get(ENDPOINTS.CATALOG.CATEGORIES),
        apiClient.get(ENDPOINTS.CATALOG.BRANDS),
      ]);
      if (catRes.status === 'fulfilled') setCategories(catRes.value.data || catRes.value || []);
      if (brandRes.status === 'fulfilled') {
        const bData = brandRes.value.data || brandRes.value;
        setBrands(bData.content || bData || []);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const fetchProducts = async (p = 0) => {
    setLoading(true);
    try {
      const res = await apiClient.get(`${ENDPOINTS.CATALOG.PRODUCTS}?page=${p}&size=15`);
      const pageData = res.data || res;
      setProducts(pageData.content || []);
      setPage(pageData.page ?? p);
      setTotalPages(pageData.totalPages ?? 1);
      setTotalElements(pageData.totalElements ?? 0);
    } finally {
      setLoading(false);
    }
  };

  const fetchIngredients = async () => {
    try {
      setIngredients(await ingredientCatalog((page) => apiClient.get(`${ENDPOINTS.CATALOG.INGREDIENTS}?page=${page}&size=100`)));
    } catch (e) {
      console.error(e);
    }
  };

  const fetchAttributes = async () => {
    try {
      const res = await apiClient.get(ENDPOINTS.ATTRIBUTES.LIST);
      const data = res.data || res;
      setAttributes(Array.isArray(data) ? data : []);
    } catch (e) {
      console.error(e);
    }
  };

  const fetchTags = async () => {
    try {
      const res = await apiClient.get(ENDPOINTS.CATALOG.TAGS);
      const data = res.data || res;
      setTags(Array.isArray(data) ? data : []);
    } catch (e) {
      console.error(e);
    }
  };

  const fetchBanners = async () => {
    try {
      const res = await apiClient.get(ENDPOINTS.CATALOG.BANNERS || '/api/v1/admin/banners');
      const data = res.data || res;
      setBanners(Array.isArray(data) ? data : []);
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    fetchCatalogs();
    if (activeTab === 'products') fetchProducts(0);
    if (activeTab === 'ingredients') fetchIngredients();
    if (activeTab === 'attributes') fetchAttributes();
    if (activeTab === 'tags') fetchTags();
    if (activeTab === 'banners') fetchBanners();
  }, [activeTab]);

  // ==========================================
  // HANDLERS: PRODUCTS & VARIANTS
  // ==========================================
  const handleOpenCreateProduct = () => {
    setEditingProduct(null);
    setHasFragrance(false); setHasAlcohol(false); setKeyActivesSummary('');
    setProductDetailLoading(false);
    setName('');
    setSlug('');
    setShortDescription('');
    setDescription('');
    setInitialVariantPrice('250000');
    setInitialVariantVolume('50ml');
    setCategoryId(categories[0]?.id ? String(categories[0].id) : '');
    setBrandId(brands[0]?.id ? String(brands[0].id) : '');
    setIsPublished(true);
    setProductType('PRODUCT'); setTargetGender('UNISEX'); setSkinType('ALL_SKIN'); setIngredientsText('');
    setHowToUse(''); setOriginCountry(''); setProductVolume('50ml'); setIsFeatured(false);
    setUploadedUrl('');
    setProductFormError('');
    setProductModalOpen(true);
  };

  const handleOpenEditProduct = async (p) => {
    // 1. Gán ngay dữ liệu từ dòng đã có để mở modal tức thì không gây lag UI
    setEditingProduct(p);
    setProductDetailLoading(true);
    setHasFragrance(Boolean(p.hasFragrance));
    setHasAlcohol(Boolean(p.hasAlcohol));
    setKeyActivesSummary(p.keyActivesSummary || '');
    setName(p.name || '');
    setSlug(p.slug || '');
    setShortDescription(p.shortDescription || '');
    setDescription(p.description || '');
    setCategoryId(p.categoryId ? String(p.categoryId) : (categories[0]?.id ? String(categories[0].id) : ''));
    setBrandId(p.brandId ? String(p.brandId) : (brands[0]?.id ? String(brands[0].id) : ''));
    setIsPublished(p.status === 'ACTIVE');
    setProductType(p.productType || 'PRODUCT');
    setTargetGender(p.targetGender || 'UNISEX');
    setSkinType(p.skinType || 'ALL_SKIN');
    setIngredientsText(p.ingredients || '');
    setHowToUse(p.howToUse || '');
    setOriginCountry(p.originCountry || '');
    setProductVolume(p.volume || '');
    setIsFeatured(Boolean(p.isFeatured));
    setUploadedUrl(p.thumbnailUrl || '');
    setProductFormError('');
    setProductModalOpen(true);

    // 2. Tải thêm chi tiết đầy đủ trong nền (có fallback sang public endpoint)
    try {
      let res;
      try {
        res = await apiClient.get(ENDPOINTS.CATALOG.PRODUCT_DETAIL(p.id));
      } catch {
        res = await apiClient.get(`/api/v1/products/${p.id}`);
      }
      const detail = res?.data || res;
      if (detail && detail.id) {
        setEditingProduct(detail);
        setHasFragrance(Boolean(detail.hasFragrance));
        setHasAlcohol(Boolean(detail.hasAlcohol));
        setKeyActivesSummary(detail.keyActivesSummary || '');
        setProductDetailLoading(false);
        if (detail.name) setName(detail.name);
        if (detail.slug) setSlug(detail.slug);
        if (detail.shortDescription !== undefined) setShortDescription(detail.shortDescription || '');
        if (detail.description !== undefined) setDescription(detail.description || '');
        if (detail.categories && detail.categories.length > 0) {
          setCategoryId(String(detail.categories[0].id));
        } else if (detail.categoryId) {
          setCategoryId(String(detail.categoryId));
        }
        if (detail.brand && detail.brand.id) {
          setBrandId(String(detail.brand.id));
        } else if (detail.brandId) {
          setBrandId(String(detail.brandId));
        }
        if (detail.status) setIsPublished(detail.status === 'ACTIVE');
        if (detail.productType) setProductType(detail.productType);
        if (detail.targetGender) setTargetGender(detail.targetGender);
        if (detail.skinType) setSkinType(detail.skinType);
        if (detail.ingredients) setIngredientsText(detail.ingredients);
        if (detail.howToUse) setHowToUse(detail.howToUse);
        if (detail.originCountry) setOriginCountry(detail.originCountry);
        if (detail.volume) setProductVolume(detail.volume);
        if (detail.isFeatured !== undefined) setIsFeatured(Boolean(detail.isFeatured));
        if (detail.thumbnailUrl) setUploadedUrl(detail.thumbnailUrl);
      }
    } catch (err) {
      console.warn('Could not fetch extra product detail:', err);
      setProductFormError('Không tải được chi tiết; chưa thể lưu để tránh ghi đè dữ liệu. Đóng và mở lại để thử lại.');
    }
  };

  const handleUploadImage = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Hiển thị xem trước ảnh cục bộ ngay lập tức
    const localBlob = URL.createObjectURL(file);
    setUploadedUrl(localBlob);
    setUploading(true);
    setProductFormError('');

    try {
      const data = await uploadFile(ENDPOINTS.CATALOG.MEDIA_UPLOAD, file);
      const uploaded = data?.url || data?.publicUrl || (typeof data === 'string' ? data : '');
      if (uploaded) {
        setUploadedUrl(uploaded);
      } else {
        throw new Error('Máy chủ không phản hồi đường dẫn ảnh hợp lệ');
      }
    } catch (err) {
      alert(err.message || 'Lỗi tải ảnh lên. Hỗ trợ JPG, PNG, WEBP, GIF, SVG, AVIF (Tối đa 10MB)');
      setProductFormError('Lỗi tải ảnh: ' + (err.message || 'Máy chủ từ chối file'));
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  const handleSubmitProduct = async (e) => {
    e.preventDefault();
    if (productDetailLoading || submittingProduct) return;
    if (uploading) {
      setProductFormError('Ảnh đang được tải lên máy chủ, vui lòng đợi trong giây lát...');
      return;
    }

    let finalThumbnail = uploadedUrl ? uploadedUrl.trim() : null;
    if (finalThumbnail && finalThumbnail.startsWith('blob:')) {
      setProductFormError('Ảnh chưa được tải lên máy chủ thành công. Vui lòng chọn lại ảnh hoặc nhập link.');
      return;
    }

    setSubmittingProduct(true);
    setProductFormError('');
    try {
      const normalizedSlug = slugify(slug || name);
      const payload = {
        name: name.trim(),
        slug: normalizedSlug,
        shortDescription: shortDescription.trim(),
        description: description.trim(),
        categoryIds: categoryId ? [parseInt(categoryId, 10)] : [],
        brandId: brandId ? parseInt(brandId, 10) : undefined,
        thumbnailUrl: finalThumbnail !== null && finalThumbnail !== undefined ? finalThumbnail : null,
        status: isPublished ? 'ACTIVE' : 'INACTIVE',
        productType,
        targetGender,
        skinType,
        ingredients: ingredientsText.trim(),
        hasFragrance,
        hasAlcohol,
        keyActivesSummary: keyActivesSummary.trim(),
        howToUse: howToUse.trim() || null,
        originCountry: originCountry.trim() || null,
        volume: productVolume.trim() || null,
        isFeatured,
      };

      if (editingProduct && editingProduct.id) {
        try {
          await apiClient.put(ENDPOINTS.CATALOG.PRODUCT_DETAIL(editingProduct.id), payload);
        } catch {
          await apiClient.put(`/api/v1/products/${editingProduct.id}`, payload);
        }
      } else {
        const createPayload = {
          ...payload,
          variants: [
            {
              sku: `${normalizedSlug.toUpperCase().replace(/[^A-Z0-9]+/g, '-')}-DEF`,
              variantName: initialVariantVolume ? initialVariantVolume.trim() : 'Tiêu chuẩn',
              price: initialVariantPrice ? parseFloat(initialVariantPrice) : 0,
              volume: initialVariantVolume.trim() || undefined,
              isDefault: true,
            }
          ]
        };
        const response = await apiClient.post(ENDPOINTS.CATALOG.PRODUCTS, createPayload);
        const created = response.data ?? response;
        if (created.id) setFeatureProduct(created);
      }
      setProductModalOpen(false);
      fetchProducts(page);
    } catch (err) {
      setProductFormError(err.message || 'Lỗi lưu thông tin sản phẩm');
    } finally {
      setSubmittingProduct(false);
    }
  };

  const handleDeleteProduct = async (id) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa sản phẩm #${id}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.CATALOG.PRODUCT_DETAIL(id));
      fetchProducts(page);
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa sản phẩm');
    }
  };

  const handleOpenVariants = async (product) => {
    setSelectedProductForVariants(product);
    try {
      const res = await apiClient.get(ENDPOINTS.CATALOG.PRODUCT_DETAIL(product.id));
      const pDetail = res.data || res;
      setVariantList(pDetail.variants || []);
    } catch {
      setVariantList([]);
    }
    setVariantsModalOpen(true);
  };

  const handleOpenCreateVariant = () => {
    setEditingVariant(null);
    setVarSku('');
    setVarName('');
    setVarPrice('');
    setVarDiscountPrice('');
    setVarVolume('');
    setVarColor('');
    setVarBarcode('');
    setVariantModalOpen(true);
  };

  const handleOpenEditVariant = (v) => {
    setEditingVariant(v);
    setVarSku(v.sku || '');
    setVarName(v.variantName || '');
    setVarPrice(v.price !== undefined && v.price !== null ? String(v.price) : '');
    setVarDiscountPrice(v.discountPrice !== undefined && v.discountPrice !== null ? String(v.discountPrice) : '');
    setVarVolume(v.volume || '');
    setVarColor(v.color || '');
    setVarBarcode(v.barcode || '');
    setVariantModalOpen(true);
  };

  const handleAddVariant = async (e) => {
    e.preventDefault();
    try {
      const payload = {
        sku: varSku.trim(),
        variantName: varName.trim(),
        price: parseFloat(varPrice),
        discountPrice: varDiscountPrice ? parseFloat(varDiscountPrice) : undefined,
        volume: varVolume.trim() || undefined,
        color: varColor.trim() || undefined,
        barcode: varBarcode.trim() || undefined,
        isDefault: editingVariant ? Boolean(editingVariant.isDefault) : variantList.length === 0,
        isActive: true,
      };
      if (editingVariant && editingVariant.id) {
        await apiClient.put(ENDPOINTS.CATALOG.VARIANT_DETAIL(selectedProductForVariants.id, editingVariant.id), payload);
      } else {
        await apiClient.post(ENDPOINTS.CATALOG.VARIANTS(selectedProductForVariants.id), payload);
      }
      setVariantModalOpen(false);
      // Reload variants
      const res = await apiClient.get(ENDPOINTS.CATALOG.PRODUCT_DETAIL(selectedProductForVariants.id));
      const pDetail = res.data || res;
      setVariantList(pDetail.variants || []);
      fetchProducts(page);
    } catch (err) {
      alert(err.message || 'Lỗi lưu biến thể');
    }
  };

  const handleDeleteVariant = async (variantId) => {
    if (!window.confirm('Xóa biến thể SKU này?')) return;
    try {
      await apiClient.delete(ENDPOINTS.CATALOG.VARIANT_DETAIL(selectedProductForVariants.id, variantId));
      setVariantList(variantList.filter((v) => v.id !== variantId));
    } catch (err) {
      alert(err.message || 'Lỗi xóa biến thể');
    }
  };

  // ==========================================
  // HANDLERS: CATEGORIES
  // ==========================================
  const handleOpenCreateCategory = () => {
    setEditingCategory(null);
    setCatName('');
    setCatSlug('');
    setCatDesc('');
    setCatParentId('');
    setCatOrder('0');
    setCategoryModalOpen(true);
  };

  const handleOpenEditCategory = (cat) => {
    setEditingCategory(cat);
    setCatName(cat.name || '');
    setCatSlug(cat.slug || '');
    setCatDesc(cat.description || '');
    setCatParentId(cat.parentId ? String(cat.parentId) : '');
    setCatOrder(String(cat.displayOrder || '0'));
    setCategoryModalOpen(true);
  };

  const handleSubmitCategory = async (e) => {
    e.preventDefault();
    setCatSubmitting(true);
    try {
      const payload = {
        name: catName.trim(),
        slug: slugify(catSlug || catName),
        description: catDesc.trim(),
        parentId: catParentId ? parseInt(catParentId, 10) : undefined,
        displayOrder: parseInt(catOrder, 10) || 0,
        isPublished: true,
      };
      if (editingCategory) {
        await apiClient.put(`${ENDPOINTS.CATALOG.CATEGORIES}/${editingCategory.id}`, payload);
      } else {
        await apiClient.post(ENDPOINTS.CATALOG.CATEGORIES, payload);
      }
      setCategoryModalOpen(false);
      fetchCatalogs();
    } catch (err) {
      alert(err.message || 'Lỗi lưu danh mục');
    } finally {
      setCatSubmitting(false);
    }
  };

  const handleDeleteCategory = async (id) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa danh mục này?')) return;
    try {
      await apiClient.delete(`${ENDPOINTS.CATALOG.CATEGORIES}/${id}`);
      fetchCatalogs();
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa danh mục');
    }
  };

  // ==========================================
  // HANDLERS: BRANDS
  // ==========================================
  const handleOpenCreateBrand = () => {
    setEditingBrand(null);
    setBrandName('');
    setBrandSlug('');
    setBrandCountry('Pháp');
    setBrandLogo('');
    setBrandDesc('');
    setBrandModalOpen(true);
  };

  const handleOpenEditBrand = (b) => {
    setEditingBrand(b);
    setBrandName(b.name || '');
    setBrandSlug(b.slug || '');
    setBrandCountry(b.originCountry || 'Pháp');
    setBrandLogo(b.logoUrl || '');
    setBrandDesc(b.description || '');
    setBrandModalOpen(true);
  };

  const handleSubmitBrand = async (e) => {
    e.preventDefault();
    setBrandSubmitting(true);
    try {
      const payload = {
        name: brandName.trim(),
        slug: slugify(brandSlug || brandName),
        originCountry: brandCountry.trim(),
        logoUrl: brandLogo.trim() || undefined,
        description: brandDesc.trim(),
      };
      if (editingBrand) {
        await apiClient.put(`${ENDPOINTS.CATALOG.BRANDS}/${editingBrand.id}`, payload);
      } else {
        await apiClient.post(ENDPOINTS.CATALOG.BRANDS, payload);
      }
      setBrandModalOpen(false);
      fetchCatalogs();
    } catch (err) {
      alert(err.message || 'Lỗi lưu thương hiệu');
    } finally {
      setBrandSubmitting(false);
    }
  };

  const handleDeleteBrand = async (id) => {
    if (!window.confirm('Bạn có chắc muốn xóa thương hiệu này?')) return;
    try {
      await apiClient.delete(`${ENDPOINTS.CATALOG.BRANDS}/${id}`);
      fetchCatalogs();
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa thương hiệu');
    }
  };

  // ==========================================
  // HANDLERS: INGREDIENTS
  // ==========================================
  const handleOpenCreateIngredient = () => {
    setEditingIngredient(null);
    setIngName('');
    setIngInci('');
    setIngSlug('');
    setIngEwg('1');
    setIngActive(false);
    setIngDesc('');
    setIngFunctions(''); setIngBenefits(''); setIngConcerns('');
    setIngModalOpen(true);
  };

  const handleOpenEditIngredient = (ing) => {
    setEditingIngredient(ing);
    setIngName(ing.name || '');
    setIngInci(ing.inciName || '');
    setIngSlug(ing.slug || '');
    setIngEwg(String(ing.ewgScore || '1'));
    setIngActive(Boolean(ing.activeIngredient ?? ing.isActiveIngredient));
    setIngDesc(ing.description || '');
    setIngFunctions((ing.functions || []).join('\n'));
    setIngBenefits((ing.benefits || []).join('\n'));
    setIngConcerns((ing.potentialConcerns || []).join('\n'));
    setIngModalOpen(true);
  };

  const handleSubmitIngredient = async (e) => {
    e.preventDefault();
    setIngSubmitting(true);
    try {
      const payload = {
        name: ingName.trim(),
        inciName: ingInci.trim(),
        slug: slugify(ingSlug || ingName),
        ewgScore: parseInt(ingEwg, 10) || 1,
        activeIngredient: ingActive,
        description: ingDesc.trim(),
        functions: lines(ingFunctions),
        benefits: lines(ingBenefits),
        potentialConcerns: lines(ingConcerns),
      };
      if (editingIngredient) {
        await apiClient.put(`${ENDPOINTS.CATALOG.INGREDIENTS}/${editingIngredient.id}`, payload);
      } else {
        await apiClient.post(ENDPOINTS.CATALOG.INGREDIENTS, payload);
      }
      setIngModalOpen(false);
      fetchIngredients();
    } catch (err) {
      alert(err.message || 'Lỗi lưu hoạt chất');
    } finally {
      setIngSubmitting(false);
    }
  };

  const handleDeleteIngredient = async (id) => {
    if (!window.confirm('Xóa hoạt chất mỹ phẩm này?')) return;
    try {
      await apiClient.delete(`${ENDPOINTS.CATALOG.INGREDIENTS}/${id}`);
      fetchIngredients();
    } catch (err) {
      alert(err.message || 'Lỗi xóa hoạt chất');
    }
  };

  // ==========================================
  // HANDLERS: ATTRIBUTES
  // ==========================================
  const handleOpenCreateAttr = () => {
    setEditingAttr(null);
    setAttrName('');
    setAttrDataType('STRING');
    setAttrDesc('');
    setAttrModalOpen(true);
  };

  const handleOpenEditAttr = (attr) => {
    setEditingAttr(attr);
    setAttrName(attr.name || '');
    setAttrDataType(attr.dataType || 'STRING');
    setAttrDesc(attr.description || '');
    setAttrModalOpen(true);
  };

  const handleSubmitAttr = async (e) => {
    e.preventDefault();
    setAttrSubmitting(true);
    try {
      const payload = {
        name: attrName.trim(),
        dataType: attrDataType,
        description: attrDesc.trim(),
      };
      if (editingAttr) {
        await apiClient.put(ENDPOINTS.ATTRIBUTES.DETAIL(editingAttr.id), payload);
      } else {
        await apiClient.post(ENDPOINTS.ATTRIBUTES.LIST, payload);
      }
      setAttrModalOpen(false);
      fetchAttributes();
    } catch (err) {
      alert(err.message || 'Lỗi lưu thuộc tính');
    } finally {
      setAttrSubmitting(false);
    }
  };

  const handleDeleteAttr = async (id) => {
    if (!window.confirm('Xóa định nghĩa này sẽ xóa toàn bộ giá trị thuộc tính liên quan trên các sản phẩm/SKU. Tiếp tục?')) return;
    try {
      await apiClient.delete(ENDPOINTS.ATTRIBUTES.DETAIL(id));
      fetchAttributes();
    } catch (err) {
      alert(err.message || 'Lỗi xóa thuộc tính');
    }
  };

  // ==========================================
  // HANDLERS: TAGS
  // ==========================================
  const handleOpenCreateTag = () => {
    setEditingTag(null);
    setTagName('');
    setTagModalOpen(true);
  };

  const handleOpenEditTag = (t) => {
    setEditingTag(t);
    setTagName(t.name || '');
    setTagModalOpen(true);
  };

  const handleSubmitTag = async (e) => {
    e.preventDefault();
    setTagSubmitting(true);
    try {
      const payload = { name: tagName.trim() };
      if (editingTag) {
        await apiClient.put(ENDPOINTS.CATALOG.TAG_DETAIL(editingTag.id), payload);
      } else {
        await apiClient.post(ENDPOINTS.CATALOG.TAGS, payload);
      }
      setTagModalOpen(false);
      fetchTags();
    } catch (err) {
      alert(err.message || 'Lỗi lưu thẻ');
    } finally {
      setTagSubmitting(false);
    }
  };

  const handleDeleteTag = async (id) => {
    if (!window.confirm('Xóa thẻ nhãn sản phẩm này?')) return;
    try {
      await apiClient.delete(ENDPOINTS.CATALOG.TAG_DETAIL(id));
      fetchTags();
    } catch (err) {
      alert(err.message || 'Lỗi xóa thẻ');
    }
  };

  // ==========================================
  // HANDLERS: BANNERS
  // ==========================================
  const handleOpenCreateBanner = () => {
    setEditingBanner(null);
    setBannerForm({
      title: '',
      badge: '',
      description: '',
      imageUrl: '',
      targetUrl: 'products',
      ctaText: 'Mua Sắm Ngay',
      position: 'HERO_SLIDE',
      sortOrder: (banners.length || 0) + 1,
      isActive: true,
    });
    setBannerModalOpen(true);
  };

  const handleOpenEditBanner = (b) => {
    setEditingBanner(b);
    setBannerForm({
      title: b.title || '',
      badge: b.badge || '',
      description: b.description || '',
      imageUrl: b.imageUrl || '',
      targetUrl: b.targetUrl || '',
      ctaText: b.ctaText || '',
      position: b.position || 'HERO_SLIDE',
      sortOrder: b.sortOrder ?? 0,
      isActive: b.isActive ?? true,
    });
    setBannerModalOpen(true);
  };

  const handleSubmitBanner = async (e) => {
    e.preventDefault();
    setBannerSubmitting(true);
    try {
      if (editingBanner) {
        await apiClient.put(ENDPOINTS.CATALOG.BANNER_DETAIL(editingBanner.id), bannerForm);
      } else {
        await apiClient.post(ENDPOINTS.CATALOG.BANNERS, bannerForm);
      }
      setBannerModalOpen(false);
      fetchBanners();
    } catch (err) {
      alert(err.message || 'Lỗi lưu banner');
    } finally {
      setBannerSubmitting(false);
    }
  };

  const handleDeleteBanner = async (id) => {
    if (!window.confirm(`Xóa banner #${id}?`)) return;
    try {
      await apiClient.delete(ENDPOINTS.CATALOG.BANNER_DETAIL(id));
      fetchBanners();
    } catch (err) {
      alert(err.message || 'Lỗi xóa banner');
    }
  };

  // ==========================================
  // HANDLERS: IMAGES GALLERY
  // ==========================================
  const handleOpenImages = async (product) => {
    setSelectedProductForImages(product);
    setImageList(product.images || []);
    setNewImageUrl('');
    setNewImageAlt(product.name || '');
    setNewImagePrimary(false);
    setNewImageOrder('0');
    setImagesModalOpen(true);
    setImageLoading(true);
    try {
      const res = await apiClient.get(ENDPOINTS.CATALOG.PRODUCT_DETAIL(product.id));
      const pDetail = res.data || res;
      setImageList(pDetail.images || []);
    } catch (err) {
      console.error(err);
    } finally {
      setImageLoading(false);
    }
  };

  const handleAddImage = async (e) => {
    e.preventDefault();
    if (!newImageUrl.trim() || !selectedProductForImages) return;
    setImageSubmitting(true);
    try {
      await apiClient.post(ENDPOINTS.CATALOG.IMAGES(selectedProductForImages.id), {
        imageUrl: newImageUrl.trim(),
        altText: newImageAlt.trim() || undefined,
        isPrimary: newImagePrimary,
        displayOrder: parseInt(newImageOrder, 10) || 0,
      });
      setNewImageUrl('');
      setNewImagePrimary(false);
      // Refresh images
      const res = await apiClient.get(ENDPOINTS.CATALOG.PRODUCT_DETAIL(selectedProductForImages.id));
      const pDetail = res.data || res;
      setImageList(pDetail.images || []);
      fetchProducts(page);
    } catch (err) {
      alert(err.message || 'Lỗi thêm ảnh vào bộ sưu tập');
    } finally {
      setImageSubmitting(false);
    }
  };

  const handleDeleteImage = async (imageId) => {
    if (!window.confirm('Xóa ảnh này khỏi bộ sưu tập?')) return;
    try {
      await apiClient.delete(ENDPOINTS.CATALOG.IMAGE_DETAIL(selectedProductForImages.id, imageId));
      const res = await apiClient.get(ENDPOINTS.CATALOG.PRODUCT_DETAIL(selectedProductForImages.id));
      const pDetail = res.data || res;
      setImageList(pDetail.images || []);
      fetchProducts(page);
    } catch (err) {
      alert(err.message || 'Lỗi xóa ảnh');
    }
  };

  // ==========================================
  // HANDLERS: DERMATOLOGY & ROUTINE
  // ==========================================
  const handleOpenDerma = async (product) => {
    setSelectedProductForDerma(product);
    setDermaWhenToUse('');
    setDermaFrequency('1-2 lần/ngày');
    setDermaInstructions('');
    setDermaWarnings('');
    setDermaModalOpen(true);
    try {
      const res = await apiClient.get(ENDPOINTS.CATALOG.DERMATOLOGY_PROFILE(product.id));
      const data = res.data || res;
      const usage = data?.usage || data?.usageDetail;
      if (usage) {
        setDermaWhenToUse(Array.isArray(usage.whenToUse) ? usage.whenToUse.join('\n') : '');
        setDermaFrequency(usage.frequency || '1-2 lần/ngày');
        setDermaInstructions(Array.isArray(usage.instructions) ? usage.instructions.join('\n') : '');
        setDermaWarnings(Array.isArray(usage.warnings) ? usage.warnings.join('\n') : '');
      }
    } catch (err) {
      console.warn('Could not fetch dermatology profile', err);
    }
  };

  const handleSaveDermaUsage = async (e) => {
    e.preventDefault();
    if (!selectedProductForDerma) return;
    setDermaSubmitting(true);
    try {
      await apiClient.put(ENDPOINTS.CATALOG.DERMATOLOGY_USAGE(selectedProductForDerma.id), {
        whenToUse: dermaWhenToUse.split('\n').map((s) => s.trim()).filter(Boolean),
        frequency: dermaFrequency.trim(),
        instructions: dermaInstructions.split('\n').map((s) => s.trim()).filter(Boolean),
        warnings: dermaWarnings.split('\n').map((s) => s.trim()).filter(Boolean),
      });
      setDermaModalOpen(false);
      fetchProducts(page);
    } catch (err) {
      alert(err.message || 'Lỗi lưu hướng dẫn sử dụng và da liễu');
    } finally {
      setDermaSubmitting(false);
    }
  };

  // ==========================================
  // TABLE COLUMNS
  // ==========================================
  const productColumns = [
    {
      header: 'ID',
      accessor: 'id',
      width: '60px',
    },
    {
      header: 'Ảnh',
      accessor: (row) =>
        row.thumbnailUrl ? (
          <img
            src={resolveMediaUrl(row.thumbnailUrl)}
            alt={row.name}
            style={{ width: '36px', height: '36px', objectFit: 'cover', border: '1px solid var(--border-subtle)', borderRadius: '4px' }}
            onError={(e) => { e.currentTarget.style.opacity = '0.3'; }}
          />
        ) : (
          <div style={{ width: '36px', height: '36px', backgroundColor: 'var(--color-primary-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: '4px' }}>
            <Image size={16} color="var(--text-muted)" />
          </div>
        ),
      render: (row) =>
        row.thumbnailUrl ? (
          <img
            src={resolveMediaUrl(row.thumbnailUrl)}
            alt={row.name}
            style={{ width: '36px', height: '36px', objectFit: 'cover', border: '1px solid var(--border-subtle)', borderRadius: '4px' }}
            onError={(e) => { e.currentTarget.style.opacity = '0.3'; }}
          />
        ) : (
          <div style={{ width: '36px', height: '36px', backgroundColor: 'var(--color-primary-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: '4px' }}>
            <Image size={16} color="var(--text-muted)" />
          </div>
        ),
      width: '60px',
    },
    {
      header: 'Tên sản phẩm',
      accessor: (row) => (
        <div>
          <div style={{ fontWeight: 600, color: 'var(--text-main)' }}>{row.name}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
            /{row.slug}
          </div>
        </div>
      ),
    },
    {
      header: 'Danh mục',
      accessor: (row) => row.categoryName || 'Mặc định',
      width: '140px',
    },
    {
      header: 'Thương hiệu',
      accessor: (row) => row.brandName || 'Chính hãng',
      width: '120px',
    },
    {
      header: 'Giá bán biến thể',
      accessor: (row) => {
        const min = row.minPrice !== undefined ? row.minPrice : row.basePrice;
        const max = row.maxPrice !== undefined ? row.maxPrice : min;
        if (min === undefined || min === null) return '-';
        if (max && Number(max) > Number(min)) {
          return (
            <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(min)} - {formatCurrency(max)}
            </span>
          );
        }
        return (
          <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
            {formatCurrency(min)}
          </span>
        );
      },
      align: 'right',
      width: '140px',
    },
    {
      header: 'Trạng thái',
      accessor: (row) => (
        <Badge variant={row.status === 'ACTIVE' ? 'success' : 'default'}>
          {row.status === 'ACTIVE' ? 'Đang bán' : 'Tạm ẩn'}
        </Badge>
      ),
      width: '100px',
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div className="table-actions">
          <Button variant="outline" size="sm" onClick={(e) => { e.stopPropagation(); setFeatureProduct(row); }} icon={Sparkles}>
            Thuộc tính & Thành phần
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleOpenVariants(row); }}
            icon={Sliders}
            title="Quản lý Biến thể SKU"
          >
            Biến thể
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleOpenImages(row); }}
            icon={Image}
            title="Quản lý ảnh sản phẩm"
          >
            Ảnh
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleOpenDerma(row); }}
            icon={BookOpen}
            title="Quy trình HDSD & Da liễu"
          >
            HDSD
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleOpenEditProduct(row); }}
            icon={Edit}
            title="Sửa thông tin sản phẩm"
          >
            Sửa
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleDeleteProduct(row.id); }}
            icon={Trash2}
            title="Xóa sản phẩm"
          />
        </div>
      ),
      render: (row) => (
        <div className="table-actions">
          <Button variant="outline" size="sm" onClick={(e) => { e.stopPropagation(); setFeatureProduct(row); }} icon={Sparkles}>
            Thuộc tính & Thành phần
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleOpenVariants(row); }}
            icon={Sliders}
            title="Quản lý Biến thể SKU"
          >
            Biến thể
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleOpenImages(row); }}
            icon={Image}
            title="Quản lý ảnh sản phẩm"
          >
            Ảnh
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleOpenDerma(row); }}
            icon={BookOpen}
            title="Quy trình HDSD & Da liễu"
          >
            HDSD
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleOpenEditProduct(row); }}
            icon={Edit}
            title="Sửa thông tin sản phẩm"
          >
            Sửa
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => { e.stopPropagation(); handleDeleteProduct(row.id); }}
            icon={Trash2}
            title="Xóa sản phẩm"
          />
        </div>
      ),
      align: 'right',
      width: '320px',
    },
  ];

  const attributeColumns = [
    { header: 'ID', accessor: 'id', width: '70px' },
    { header: 'Tên thuộc tính', accessor: (row) => <strong style={{ color: 'var(--text-main)' }}>{row.name}</strong> },
    { header: 'Mã Code', accessor: (row) => <code style={{ fontFamily: 'var(--font-mono)', fontSize: '12px' }}>{row.code}</code> },
    { header: 'Kiểu dữ liệu', accessor: (row) => <Badge variant="info">{row.dataType}</Badge>, width: '130px' },
    { header: 'Mô tả', accessor: (row) => row.description || '—' },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div className="table-actions">
          <Button variant="outline" size="sm" onClick={() => handleOpenEditAttr(row)} icon={Edit} title="Sửa thuộc tính" />
          <Button variant="outline" size="sm" onClick={() => handleDeleteAttr(row.id)} icon={Trash2} title="Xóa thuộc tính" />
        </div>
      ),
      align: 'right',
      width: '110px',
    },
  ];

  const tagColumns = [
    { header: 'ID', accessor: 'id', width: '70px' },
    { header: 'Tên thẻ', accessor: (row) => <strong style={{ color: 'var(--text-main)' }}>{row.name}</strong> },
    { header: 'Slug', accessor: (row) => <code style={{ fontFamily: 'var(--font-mono)', fontSize: '12px' }}>{row.slug}</code> },
    { header: 'Số sản phẩm gắn', accessor: (row) => row.productCount ?? 0, width: '140px', align: 'center' },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div className="table-actions">
          <Button variant="outline" size="sm" onClick={() => handleOpenEditTag(row)} icon={Edit} title="Sửa thẻ" />
          <Button variant="outline" size="sm" onClick={() => handleDeleteTag(row.id)} icon={Trash2} title="Xóa thẻ" />
        </div>
      ),
      align: 'right',
      width: '110px',
    },
  ];

  const bannerColumns = [
    { header: 'ID', accessor: 'id', width: '50px' },
    {
      header: 'Hình ảnh',
      accessor: (row) => (
        <div style={{ width: '84px', height: '48px', borderRadius: '4px', overflow: 'hidden', backgroundColor: '#F3F4F6' }}>
          <img src={row.imageUrl} alt={row.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={(e) => { e.target.style.display = 'none'; }} />
        </div>
      ),
      width: '100px'
    },
    {
      header: 'Tiêu đề & Badge',
      accessor: (row) => (
        <div>
          {row.badge && (
            <span style={{ fontSize: '10.5px', fontWeight: 800, color: 'var(--color-primary-600)', backgroundColor: 'var(--color-primary-50)', padding: '1px 6px', borderRadius: '3px', marginRight: '6px' }}>
              {row.badge}
            </span>
          )}
          <strong style={{ fontSize: '13px' }}>{row.title}</strong>
          {row.description && <div style={{ fontSize: '11.5px', color: 'var(--text-muted)', marginTop: '2px', display: '-webkit-box', WebkitLineClamp: 1, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{row.description}</div>}
        </div>
      )
    },
    {
      header: 'Vị trí',
      accessor: (row) => (
        <Badge variant={row.position === 'HERO_SLIDE' ? 'primary' : row.position === 'HERO_SIDE' ? 'warning' : 'default'}>
          {row.position === 'HERO_SLIDE' ? 'Banner chính' : row.position === 'HERO_SIDE' ? 'Banner phụ' : (row.position || 'Mặc định')}
        </Badge>
      ),
      width: '120px'
    },
    {
      header: 'Đích liên kết',
      accessor: (row) => <code style={{ fontSize: '11.5px' }}>{row.targetUrl || '—'}</code>,
      width: '130px'
    },
    { header: 'Thứ tự', accessor: 'sortOrder', width: '70px', align: 'center' },
    {
      header: 'Trạng thái',
      accessor: (row) => <Badge variant={row.isActive ? 'success' : 'default'}>{row.isActive ? 'Đang bật' : 'Tắt'}</Badge>,
      width: '90px'
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div className="table-actions">
          <Button variant="outline" size="sm" onClick={() => handleOpenEditBanner(row)} icon={Edit} title="Sửa banner" />
          <Button variant="outline" size="sm" onClick={() => handleDeleteBanner(row.id)} icon={Trash2} title="Xóa banner" />
        </div>
      ),
      align: 'right',
      width: '110px'
    }
  ];

  const categoryColumns = [
    { header: 'ID', accessor: 'id', width: '70px' },
    { header: 'Tên danh mục', accessor: (row) => <strong style={{ color: 'var(--text-main)' }}>{row.name}</strong> },
    { header: 'Đường dẫn Slug', accessor: (row) => <span style={{ fontFamily: 'var(--font-mono)', fontSize: '12px' }}>/{row.slug}</span> },
    { header: 'Thứ tự hiển thị', accessor: 'displayOrder', width: '120px', align: 'center' },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div className="table-actions">
          <Button variant="outline" size="sm" onClick={() => handleOpenEditCategory(row)} icon={Edit} title="Sửa danh mục" />
          <Button variant="outline" size="sm" onClick={() => handleDeleteCategory(row.id)} icon={Trash2} title="Xóa danh mục" />
        </div>
      ),
      align: 'right',
      width: '120px',
    },
  ];

  const brandColumns = [
    { header: 'ID', accessor: 'id', width: '70px' },
    {
      header: 'Logo',
      accessor: (row) =>
        row.logoUrl ? (
          <img src={resolveMediaUrl(row.logoUrl)} alt={row.name} style={{ width: '40px', height: '24px', objectFit: 'contain' }} />
        ) : (
          <Award size={18} color="var(--text-muted)" />
        ),
      render: (row) =>
        row.logoUrl ? (
          <img src={resolveMediaUrl(row.logoUrl)} alt={row.name} style={{ width: '40px', height: '24px', objectFit: 'contain' }} />
        ) : (
          <Award size={18} color="var(--text-muted)" />
        ),
      width: '70px',
    },
    { header: 'Tên thương hiệu', accessor: (row) => <strong>{row.name}</strong> },
    { header: 'Quốc gia xuất xứ', accessor: 'originCountry', width: '140px' },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div className="table-actions">
          <Button variant="outline" size="sm" onClick={() => handleOpenEditBrand(row)} icon={Edit} title="Sửa thương hiệu" />
          <Button variant="outline" size="sm" onClick={() => handleDeleteBrand(row.id)} icon={Trash2} title="Xóa thương hiệu" />
        </div>
      ),
      align: 'right',
      width: '120px',
    },
  ];

  const ingredientColumns = [
    { header: 'ID', accessor: 'id', width: '60px' },
    { header: 'Tên thương mại', accessor: (row) => <strong>{row.name}</strong> },
    { header: 'Tên khoa học (INCI)', accessor: 'inciName' },
    {
      header: 'Điểm an toàn EWG',
      accessor: (row) => (
        <Badge variant={row.ewgScore <= 2 ? 'success' : row.ewgScore <= 5 ? 'warning' : 'danger'}>
          EWG {row.ewgScore}
        </Badge>
      ),
      width: '110px',
    },
    {
      header: 'Hoạt chất chính',
      accessor: (row) => (
        <Badge variant={(row.activeIngredient ?? row.isActiveIngredient) ? 'info' : 'default'}>
          {(row.activeIngredient ?? row.isActiveIngredient) ? 'Có' : 'Không'}
        </Badge>
      ),
      width: '110px',
    },
    {
      header: 'Thao tác',
      accessor: (row) => (
        <div className="table-actions">
          <Button variant="outline" size="sm" onClick={() => handleOpenEditIngredient(row)} icon={Edit} title="Sửa hoạt chất" />
          <Button variant="outline" size="sm" onClick={() => handleDeleteIngredient(row.id)} icon={Trash2} title="Xóa hoạt chất" />
        </div>
      ),
      align: 'right',
      width: '120px',
    },
  ];

  return (
    <div className="content-container">
      {/* Page Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Quản lý Danh mục & Sản phẩm (Catalog Hub)</h1>
          <p className="page-subtitle">
            Quản trị toàn bộ danh mục sản phẩm, biến thể SKU, thương hiệu và bảng thành phần hoạt chất
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          {activeTab === 'products' && (
            <Button variant="primary" size="sm" onClick={handleOpenCreateProduct} icon={Plus}>
              Thêm sản phẩm mới
            </Button>
          )}
          {activeTab === 'categories' && (
            <Button variant="primary" size="sm" onClick={handleOpenCreateCategory} icon={Plus}>
              Thêm danh mục
            </Button>
          )}
          {activeTab === 'brands' && (
            <Button variant="primary" size="sm" onClick={handleOpenCreateBrand} icon={Plus}>
              Thêm thương hiệu
            </Button>
          )}
          {activeTab === 'ingredients' && (
            <Button variant="primary" size="sm" onClick={handleOpenCreateIngredient} icon={Plus}>
              Thêm hoạt chất
            </Button>
          )}
          {activeTab === 'attributes' && (
            <Button variant="primary" size="sm" onClick={handleOpenCreateAttr} icon={Plus}>
              Thêm thuộc tính
            </Button>
          )}
          {activeTab === 'tags' && (
            <Button variant="primary" size="sm" onClick={handleOpenCreateTag} icon={Plus}>
              Thêm thẻ nhãn
            </Button>
          )}
          {activeTab === 'banners' && (
            <Button variant="primary" size="sm" onClick={handleOpenCreateBanner} icon={Plus}>
              Thêm banner mới
            </Button>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              if (activeTab === 'products') fetchProducts(page);
              else if (activeTab === 'categories' || activeTab === 'brands') fetchCatalogs();
              else if (activeTab === 'ingredients') fetchIngredients();
              else if (activeTab === 'attributes') fetchAttributes();
              else if (activeTab === 'tags') fetchTags();
              else if (activeTab === 'banners') fetchBanners();
            }}
            loading={loading}
            icon={RefreshCw}
          >
            Làm mới
          </Button>
        </div>
      </div>

      {/* Modernist Swiss Sub-Tabs */}
      <div className="tabs-header">
        <button
          className={`tab-btn ${activeTab === 'products' ? 'active' : ''}`}
          onClick={() => setActiveTab('products')}
        >
          <Package size={15} />
          Sản phẩm & Biến thể SKU ({totalElements})
        </button>
        <button
          className={`tab-btn ${activeTab === 'categories' ? 'active' : ''}`}
          onClick={() => setActiveTab('categories')}
        >
          <FolderTree size={15} />
          Danh mục sản phẩm ({categories.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'brands' ? 'active' : ''}`}
          onClick={() => setActiveTab('brands')}
        >
          <Award size={15} />
          Thương hiệu ({brands.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'ingredients' ? 'active' : ''}`}
          onClick={() => setActiveTab('ingredients')}
        >
          <Sparkles size={15} />
          Bảng Hoạt chất & Thành phần ({ingredients.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'attributes' ? 'active' : ''}`}
          onClick={() => setActiveTab('attributes')}
        >
          <Sliders size={15} />
          Thuộc tính mở rộng ({attributes.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'tags' ? 'active' : ''}`}
          onClick={() => setActiveTab('tags')}
        >
          <TagIcon size={15} />
          Thẻ nhãn sản phẩm ({tags.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'banners' ? 'active' : ''}`}
          onClick={() => setActiveTab('banners')}
        >
          <Image size={15} />
          Banner & Slider ({banners.length})
        </button>
      </div>

      {/* TAB CONTENT: PRODUCTS */}
      {activeTab === 'products' && (
        <div className="card">
          <DataTable
            columns={productColumns}
            data={products}
            loading={loading}
            emptyMessage="Không tìm thấy sản phẩm nào trong cơ sở dữ liệu."
          />
          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => fetchProducts(page - 1)}>
                Trang trước
              </Button>
              <span style={{ fontSize: '12px', display: 'flex', alignItems: 'center' }}>
                Trang {page + 1} / {totalPages}
              </span>
              <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => fetchProducts(page + 1)}>
                Trang sau
              </Button>
            </div>
          )}
        </div>
      )}

      {/* TAB CONTENT: CATEGORIES */}
      {activeTab === 'categories' && (
        <div className="card">
          <DataTable
            columns={categoryColumns}
            data={categories}
            loading={false}
            emptyMessage="Chưa có danh mục nào."
          />
        </div>
      )}

      {/* TAB CONTENT: BRANDS */}
      {activeTab === 'brands' && (
        <div className="card">
          <DataTable
            columns={brandColumns}
            data={brands}
            loading={false}
            emptyMessage="Chưa có thương hiệu nào."
          />
        </div>
      )}

      {/* TAB CONTENT: INGREDIENTS */}
      {activeTab === 'ingredients' && (
        <div className="card">
          <DataTable
            columns={ingredientColumns}
            data={ingredients}
            loading={false}
            emptyMessage="Chưa có hoạt chất mỹ phẩm nào trong từ điển."
          />
        </div>
      )}

      {/* TAB CONTENT: ATTRIBUTES */}
      {activeTab === 'attributes' && (
        <div className="card">
          <DataTable
            columns={attributeColumns}
            data={attributes}
            loading={false}
            emptyMessage="Chưa có định nghĩa thuộc tính nào."
          />
        </div>
      )}

      {/* TAB CONTENT: TAGS */}
      {activeTab === 'tags' && (
        <div className="card">
          <DataTable
            columns={tagColumns}
            data={tags}
            loading={false}
            emptyMessage="Chưa có thẻ nhãn sản phẩm nào."
          />
        </div>
      )}

      {/* TAB CONTENT: BANNERS */}
      {activeTab === 'banners' && (
        <div className="card">
          <DataTable
            columns={bannerColumns}
            data={banners}
            loading={false}
            emptyMessage="Chưa có banner quảng cáo nào."
          />
        </div>
      )}

      {featureProduct && <ProductFeaturesModal key={featureProduct.id} product={featureProduct} onClose={() => setFeatureProduct(null)} />}

      {/* MODAL 1: CREATE / EDIT PRODUCT */}
      <Modal
        isOpen={productModalOpen}
        onClose={() => setProductModalOpen(false)}
        title={editingProduct ? `Cập nhật sản phẩm #${editingProduct.id}` : 'Thêm sản phẩm mới'}
        maxWidth="680px"
      >
        <form onSubmit={handleSubmitProduct}>
          {productFormError && <div role="alert" style={{ color: 'var(--color-danger-700)', marginBottom: '12px', fontSize: '13px' }}>{productFormError}</div>}
          {productDetailLoading && !productFormError && <p role="status">Đang tải thông tin đầy đủ; chưa thể lưu.</p>}
          <div className="grid-2">
            <Input label="Tên sản phẩm *" value={name} onChange={(e) => setName(e.target.value)} required />
            <Input label="Đường dẫn Slug (tự tạo nếu để trống)" value={slug} onChange={(e) => setSlug(e.target.value)} />
          </div>
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Select
              label="Danh mục *"
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              options={[{ label: '-- Chọn danh mục --', value: '' }, ...categories.map((c) => ({ label: c.name, value: String(c.id) }))]}
            />
            <Select
              label="Thương hiệu *"
              value={brandId}
              onChange={(e) => setBrandId(e.target.value)}
              options={[{ label: '-- Chọn thương hiệu --', value: '' }, ...brands.map((b) => ({ label: b.name, value: String(b.id) }))]}
            />
          </div>

          {!editingProduct ? (
            <div style={{ marginTop: '14px', padding: '12px 14px', backgroundColor: 'var(--color-primary-50)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ fontSize: '12px', fontWeight: 700, textTransform: 'uppercase', marginBottom: '8px', color: 'var(--color-primary-800)' }}>
                Phiên bản / Biến thể khởi tạo ban đầu (SKU)
              </div>
              <div className="grid-2">
                <Input
                  label="Dung tích / Kích cỡ *"
                  placeholder="VD: 50ml, 236ml, 473ml"
                  value={initialVariantVolume}
                  onChange={(e) => {
                    setInitialVariantVolume(e.target.value);
                    if (!productVolume) setProductVolume(e.target.value);
                  }}
                  required
                />
                <Input
                  label="Giá bán biến thể (VNĐ) *"
                  type="number"
                  placeholder="VD: 290000"
                  value={initialVariantPrice}
                  onChange={(e) => setInitialVariantPrice(e.target.value)}
                  required
                />
              </div>
              <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                Giá sản phẩm được quản lý theo từng dung tích SKU. Bạn có thể thêm các dung tích khác sau khi tạo.
              </span>
            </div>
          ) : (
            <div style={{ marginTop: '12px', padding: '10px 12px', backgroundColor: 'var(--color-info-50)', border: '1px solid var(--color-info-600)', fontSize: '12px', color: 'var(--color-info-700)', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Info size={16} color="var(--color-info-600)" style={{ flexShrink: 0 }} />
              <span>Giá sản phẩm được quản lý độc lập theo từng phiên bản dung tích tại mục <strong>"Biến thể"</strong> ở danh sách sản phẩm.</span>
            </div>
          )}
          <div style={{ marginTop: '12px' }}>
            <Input label="Mô tả ngắn gọn" value={shortDescription} onChange={(e) => setShortDescription(e.target.value)} />
          </div>
          <div className="grid-3" style={{ marginTop: '12px' }}>
            <Select label="Loại sản phẩm" value={productType} onChange={(e) => setProductType(e.target.value)} options={[{label:'Mỹ phẩm',value:'PRODUCT'},{label:'Dịch vụ',value:'SERVICE'},{label:'Combo',value:'COMBO'}]} />
            <Select label="Đối tượng" value={targetGender} onChange={(e) => setTargetGender(e.target.value)} options={[{label:'Unisex',value:'UNISEX'},{label:'Nữ',value:'FEMALE'},{label:'Nam',value:'MALE'}]} />
            <Select label="Loại da phù hợp" value={skinType} onChange={(e) => setSkinType(e.target.value)} options={[{label:'Mọi loại da',value:'ALL_SKIN'},{label:'Da dầu',value:'OILY'},{label:'Da khô',value:'DRY'},{label:'Da hỗn hợp',value:'COMBINATION'},{label:'Da nhạy cảm',value:'SENSITIVE'},{label:'Da thường',value:'NORMAL'}]} />
          </div>
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input label="Xuất xứ" value={originCountry} onChange={(e) => setOriginCountry(e.target.value)} />
            <Input label="Dung tích quy cách" value={productVolume} onChange={(e) => setProductVolume(e.target.value)} />
          </div>
          <div style={{ marginTop: '12px' }}><label className="form-label" htmlFor="product-inci">Bảng thành phần INCI nguyên văn</label><textarea id="product-inci" className="form-textarea" rows={2} value={ingredientsText} onChange={(e) => setIngredientsText(e.target.value)} /></div>
          <p>INCI không tự gán thành phần. Sau khi tạo, chọn “Thuộc tính & Thành phần” để liên kết hoạt chất và nồng độ.</p>
          <Input label="Tóm tắt hoạt chất nổi bật" value={keyActivesSummary} onChange={(e) => setKeyActivesSummary(e.target.value)} maxLength={500} disabled={productDetailLoading} />
          <label><input type="checkbox" checked={hasFragrance} onChange={(e) => setHasFragrance(e.target.checked)} disabled={productDetailLoading} /> Có hương liệu</label>
          <label style={{ marginLeft: 12 }}><input type="checkbox" checked={hasAlcohol} onChange={(e) => setHasAlcohol(e.target.checked)} disabled={productDetailLoading} /> Có cồn khô</label>
          <div style={{ marginTop: '12px' }}><label className="form-label">Hướng dẫn sử dụng & cảnh báo</label><textarea className="form-textarea" rows={2} value={howToUse} onChange={(e) => setHowToUse(e.target.value)} /></div>
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Mô tả chi tiết sản phẩm</label>
            <textarea
              className="form-textarea"
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
          <div className="form-group" style={{ marginTop: '14px' }}>
            <label className="form-label" style={{ marginBottom: '8px', display: 'block' }}>
              Ảnh đại diện sản phẩm (Thumbnail)
            </label>
            <div className="media-upload-zone">
              <div className="media-preview-box">
                {uploadedUrl ? (
                  <img
                    src={resolveMediaUrl(uploadedUrl)}
                    alt="Thumbnail preview"
                    className="media-preview-img"
                    onError={(e) => { e.currentTarget.style.opacity = '0.3'; }}
                  />
                ) : (
                  <Image size={24} color="var(--text-muted)" />
                )}
              </div>
              <div className="media-upload-meta">
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/jpeg,image/png,image/webp,image/gif,image/svg+xml,image/avif,image/bmp"
                    onChange={handleUploadImage}
                    disabled={uploading}
                    style={{ display: 'none' }}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    icon={Upload}
                    onClick={() => fileInputRef.current?.click()}
                    disabled={uploading}
                  >
                    {uploading ? 'Đang tải ảnh...' : 'Tải ảnh từ máy tính'}
                  </Button>
                  {uploadedUrl && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => setUploadedUrl('')}
                      style={{ color: 'var(--color-danger-600)', fontSize: '12px' }}
                    >
                      Xóa ảnh
                    </Button>
                  )}
                </div>
                <Input
                  placeholder="Hoặc dán URL ảnh trực tiếp (https://...)"
                  value={uploadedUrl}
                  onChange={(e) => setUploadedUrl(e.target.value)}
                  style={{ fontSize: '12px' }}
                />
                <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                  Định dạng: JPG, PNG, WEBP, GIF, SVG, AVIF, BMP (Tối đa 10 MB)
                </span>
                <div className="media-preset-bar">
                  <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>Ảnh mẫu:</span>
                  {QUICK_SAMPLE_IMAGES.map((sample, idx) => (
                    <button
                      key={idx}
                      type="button"
                      className="preset-chip"
                      onClick={() => setUploadedUrl(sample.url)}
                    >
                      {sample.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '14px', fontSize: '13px', fontWeight: 600 }}>
            <input type="checkbox" checked={isPublished} onChange={(e) => setIsPublished(e.target.checked)} />
            Xuất bản sản phẩm ngay
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '10px', fontSize: '13px', fontWeight: 600 }}><input type="checkbox" checked={isFeatured} onChange={(e) => setIsFeatured(e.target.checked)} />Sản phẩm nổi bật</label>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setProductModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={submittingProduct} disabled={uploading || productDetailLoading}>
              {uploading ? 'Đang tải ảnh...' : 'Lưu sản phẩm'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 2: MANAGE PRODUCT VARIANTS (SKUs) */}
      <Modal
        isOpen={variantsModalOpen}
        onClose={() => setVariantsModalOpen(false)}
        title={`Quản lý Biến thể SKU: ${selectedProductForVariants?.name}`}
        maxWidth="800px"
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '14px' }}>
          <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
            Danh sách các SKU định giá, dung tích, barcode riêng biệt
          </span>
          <Button variant="primary" size="sm" icon={Plus} onClick={handleOpenCreateVariant}>
            Thêm biến thể SKU
          </Button>
        </div>

        <table className="data-table" style={{ width: '100%' }}>
          <thead>
            <tr>
              <th>Mã SKU</th>
              <th>Tên biến thể</th>
              <th>Dung tích / Màu</th>
              <th>Giá bán</th>
              <th>Barcode</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {variantList.length === 0 ? (
              <tr>
                <td colSpan={6} style={{ textAlign: 'center', padding: '20px', color: 'var(--text-muted)' }}>
                  Chưa có biến thể SKU nào. Nhấn "Thêm biến thể SKU" để cấu hình.
                </td>
              </tr>
            ) : (
              variantList.map((v) => (
                <tr key={v.id}>
                  <td>
                    <code style={{ fontWeight: 700, color: 'var(--color-accent-700)' }}>{v.sku}</code>
                    {v.isDefault && <Badge variant="info" style={{ marginLeft: '6px', fontSize: '9px' }}>Mặc định</Badge>}
                  </td>
                  <td>{v.variantName}</td>
                  <td>{v.volume || v.color || 'Mặc định'}</td>
                  <td><strong>{formatCurrency(v.price)}</strong></td>
                  <td>{v.barcode || 'N/A'}</td>
                  <td>
                    <div className="table-actions">
                      <Button variant="outline" size="sm" icon={Edit} onClick={() => handleOpenEditVariant(v)} title="Sửa biến thể" />
                      <Button variant="outline" size="sm" icon={Trash2} onClick={() => handleDeleteVariant(v.id)} title="Xóa biến thể" />
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </Modal>

      {/* MODAL 2.1: CREATE / EDIT VARIANT */}
      <Modal
        isOpen={variantModalOpen}
        onClose={() => setVariantModalOpen(false)}
        title={editingVariant ? `Cập nhật Biến thể: ${editingVariant.sku}` : "Thêm Biến thể SKU Mới"}
        maxWidth="500px"
      >
        <form onSubmit={handleAddVariant}>
          <div className="grid-2">
            <Input label="Mã SKU *" value={varSku} onChange={(e) => setVarSku(e.target.value)} required placeholder="VD: LRP-50ML" />
            <Input label="Tên biến thể *" value={varName} onChange={(e) => setVarName(e.target.value)} required placeholder="VD: Chai 50ML" />
          </div>
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input label="Giá bán (VNĐ) *" type="number" value={varPrice} onChange={(e) => setVarPrice(e.target.value)} required />
            <Input label="Giá khuyến mãi" type="number" value={varDiscountPrice} onChange={(e) => setVarDiscountPrice(e.target.value)} />
          </div>
          <div className="grid-3" style={{ marginTop: '12px' }}>
            <Input label="Dung tích" value={varVolume} onChange={(e) => setVarVolume(e.target.value)} placeholder="50ml" />
            <Input label="Màu sắc" value={varColor} onChange={(e) => setVarColor(e.target.value)} placeholder="Tone 01" />
            <Input label="Mã Barcode" value={varBarcode} onChange={(e) => setVarBarcode(e.target.value)} placeholder="893..." />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setVariantModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit">Lưu biến thể</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 3: CREATE / EDIT CATEGORY */}
      <Modal
        isOpen={categoryModalOpen}
        onClose={() => setCategoryModalOpen(false)}
        title={editingCategory ? 'Cập nhật danh mục' : 'Thêm danh mục mới'}
        maxWidth="500px"
      >
        <form onSubmit={handleSubmitCategory}>
          <Input label="Tên danh mục *" value={catName} onChange={(e) => setCatName(e.target.value)} required />
          <div style={{ marginTop: '12px' }}>
            <Input label="Đường dẫn Slug (tự tạo nếu để trống)" value={catSlug} onChange={(e) => setCatSlug(e.target.value)} />
          </div>
          <div style={{ marginTop: '12px' }}>
            <Input label="Thứ tự hiển thị" type="number" value={catOrder} onChange={(e) => setCatOrder(e.target.value)} />
          </div>
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Mô tả danh mục</label>
            <textarea className="form-textarea" rows={3} value={catDesc} onChange={(e) => setCatDesc(e.target.value)} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setCategoryModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={catSubmitting}>Lưu danh mục</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 4: CREATE / EDIT BRAND */}
      <Modal
        isOpen={brandModalOpen}
        onClose={() => setBrandModalOpen(false)}
        title={editingBrand ? 'Cập nhật thương hiệu' : 'Thêm Thương Hiệu Mới'}
        maxWidth="500px"
      >
        <form onSubmit={handleSubmitBrand}>
          <Input label="Tên thương hiệu *" value={brandName} onChange={(e) => setBrandName(e.target.value)} required />
          <div style={{ marginTop: '12px' }}>
            <Input label="Đường dẫn Slug" value={brandSlug} onChange={(e) => setBrandSlug(e.target.value)} />
          </div>
          <div style={{ marginTop: '12px' }}>
            <Input label="Quốc gia xuất xứ" value={brandCountry} onChange={(e) => setBrandCountry(e.target.value)} placeholder="Pháp, Hàn Quốc, Nhật Bản..." />
          </div>
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Logo thương hiệu</label>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '6px' }}>
              <Input label="" value={brandLogo} onChange={(e) => setBrandLogo(e.target.value)} placeholder="URL logo https://... hoặc tải từ máy" />
              {brandLogo && (
                <img src={resolveMediaUrl(brandLogo)} alt="Logo" style={{ width: '36px', height: '36px', objectFit: 'contain', border: '1px solid var(--border-subtle)', borderRadius: '4px' }} />
              )}
            </div>
            <input
              type="file"
              accept="image/*"
              onChange={async (e) => {
                const file = e.target.files?.[0];
                if (!file) return;
                try {
                  const data = await uploadFile(ENDPOINTS.CATALOG.MEDIA_UPLOAD, file);
                  const url = data?.url || data?.publicUrl;
                  if (url) setBrandLogo(url);
                } catch (err) {
                  alert(err.message || 'Lỗi tải logo');
                } finally {
                  e.target.value = '';
                }
              }}
            />
          </div>
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Giới thiệu thương hiệu</label>
            <textarea className="form-textarea" rows={3} value={brandDesc} onChange={(e) => setBrandDesc(e.target.value)} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setBrandModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={brandSubmitting}>Lưu thương hiệu</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 5: CREATE INGREDIENT */}
      <Modal
        isOpen={ingModalOpen}
        onClose={() => setIngModalOpen(false)}
        title={editingIngredient ? 'Cập nhật hoạt chất' : 'Thêm Hoạt Chất & Thành Phần Mỹ Phẩm'}
        maxWidth="500px"
      >
        <form onSubmit={handleSubmitIngredient}>
          <Input label="Tên hoạt chất (Thương mại) *" value={ingName} onChange={(e) => setIngName(e.target.value)} required placeholder="VD: Niacinamide, Retinol" />
          <div style={{ marginTop: '12px' }}>
            <Input label="Tên khoa học quốc tế (INCI Name) *" value={ingInci} onChange={(e) => setIngInci(e.target.value)} required placeholder="VD: Nicotinamide, Vitamin B3" />
          </div>
          <div style={{ marginTop: '12px' }}>
            <Input label="Mã Slug *" value={ingSlug} onChange={(e) => setIngSlug(e.target.value)} required placeholder="niacinamide" />
          </div>
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input label="Điểm an toàn EWG (1-10)" type="number" min="1" max="10" value={ingEwg} onChange={(e) => setIngEwg(e.target.value)} />
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '24px' }}>
              <input type="checkbox" id="ingActiveCheck" checked={ingActive} onChange={(e) => setIngActive(e.target.checked)} />
              <label htmlFor="ingActiveCheck" style={{ fontSize: '13px', fontWeight: 600 }}>Là hoạt chất (trong danh mục)</label>
            </div>
          </div>
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Mô tả công dụng và khuyến cáo</label>
            <textarea className="form-textarea" rows={3} value={ingDesc} onChange={(e) => setIngDesc(e.target.value)} />
          </div>
          <p>Mỗi chức năng/lợi ích/cảnh báo nhập một dòng. Chức năng phân nhóm dùng mã như humectant, exfoliant.</p>
          <label className="form-group">Chức năng<textarea className="form-textarea" rows={3} value={ingFunctions} onChange={(e) => setIngFunctions(e.target.value)} /></label>
          <label className="form-group">Lợi ích<textarea className="form-textarea" rows={3} value={ingBenefits} onChange={(e) => setIngBenefits(e.target.value)} /></label>
          <label className="form-group">Cảnh báo<textarea className="form-textarea" rows={3} value={ingConcerns} onChange={(e) => setIngConcerns(e.target.value)} /></label>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setIngModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={ingSubmitting}>Lưu hoạt chất</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 6: CREATE / EDIT ATTRIBUTE */}
      <Modal
        isOpen={attrModalOpen}
        onClose={() => setAttrModalOpen(false)}
        title={editingAttr ? `Cập nhật thuộc tính: ${editingAttr.name}` : 'Thêm Thuộc Tính Mở Rộng Mới'}
        maxWidth="500px"
      >
        <form onSubmit={handleSubmitAttr}>
          <Input label="Tên thuộc tính *" value={attrName} onChange={(e) => setAttrName(e.target.value)} required placeholder="VD: Nồng độ B5, Chỉ số PA" />
          <div style={{ marginTop: '12px' }}>
            <Select
              label="Kiểu dữ liệu *"
              value={attrDataType}
              onChange={(e) => setAttrDataType(e.target.value)}
              options={[
                { label: 'Chuỗi ngắn (STRING)', value: 'STRING' },
                { label: 'Số (NUMBER)', value: 'NUMBER' },
                { label: 'Đúng / Sai (BOOLEAN)', value: 'BOOLEAN' },
                { label: 'Ngày tháng (DATE)', value: 'DATE' },
                { label: 'Văn bản dài (TEXT_AREA)', value: 'TEXT_AREA' },
              ]}
            />
          </div>
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Mô tả chi tiết</label>
            <textarea className="form-textarea" rows={3} value={attrDesc} onChange={(e) => setAttrDesc(e.target.value)} placeholder="Mô tả công dụng hoặc phạm vi của thuộc tính" />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setAttrModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={attrSubmitting}>Lưu thuộc tính</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 7: CREATE / EDIT TAG */}
      <Modal
        isOpen={tagModalOpen}
        onClose={() => setTagModalOpen(false)}
        title={editingTag ? `Cập nhật thẻ nhãn: ${editingTag.name}` : 'Thêm Thẻ Nhãn Sản Phẩm Mới'}
        maxWidth="440px"
      >
        <form onSubmit={handleSubmitTag}>
          <Input label="Tên thẻ nhãn *" value={tagName} onChange={(e) => setTagName(e.target.value)} required placeholder="VD: Da dầu mụn, Thuần chay, Bán chạy" />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setTagModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={tagSubmitting}>Lưu thẻ</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 8: PRODUCT IMAGES GALLERY */}
      <Modal
        isOpen={imagesModalOpen}
        onClose={() => setImagesModalOpen(false)}
        title={`Bộ sưu tập hình ảnh: ${selectedProductForImages?.name || ''}`}
        maxWidth="750px"
      >
        <div>
          {/* Gallery View */}
          <div style={{ marginBottom: '20px' }}>
            <label className="form-label">Hình ảnh hiện tại ({imageList.length})</label>
            {imageLoading ? (
              <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)' }}>Đang tải hình ảnh...</div>
            ) : imageList.length === 0 ? (
              <div style={{ padding: '16px', backgroundColor: 'var(--color-primary-50)', border: '1px dashed var(--border-subtle)', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                Chưa có ảnh phụ nào trong bộ sưu tập sản phẩm này.
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))', gap: '12px', marginTop: '8px' }}>
                {imageList.map((img) => (
                  <div key={img.id} style={{ position: 'relative', border: '1px solid var(--border-subtle)', borderRadius: '8px', overflow: 'hidden', backgroundColor: '#fff', padding: '6px' }}>
                    <img
                      src={resolveMediaUrl(img.imageUrl)}
                      alt={img.altText || 'Ảnh sản phẩm'}
                      style={{ width: '100%', height: '100px', objectFit: 'cover', borderRadius: '4px' }}
                    />
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '6px', fontSize: '11px' }}>
                      {img.isPrimary ? (
                        <Badge variant="success">Ảnh chính</Badge>
                      ) : (
                        <span style={{ color: 'var(--text-muted)' }}>Thứ tự: {img.displayOrder ?? 0}</span>
                      )}
                      <button
                        type="button"
                        onClick={() => handleDeleteImage(img.id)}
                        style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: 'var(--color-danger-600)', padding: '2px' }}
                        title="Xóa ảnh"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Add Image Form */}
          <form onSubmit={handleAddImage} style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '16px' }}>
            <div style={{ fontWeight: 600, fontSize: '13px', marginBottom: '10px' }}>Thêm ảnh mới vào bộ sưu tập</div>
            <div style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
              <Input
                label="Đường dẫn ảnh (URL) *"
                value={newImageUrl}
                onChange={(e) => setNewImageUrl(e.target.value)}
                placeholder="https://... hoặc tải từ thiết bị"
                required
              />
              <div style={{ alignSelf: 'flex-end', marginBottom: '2px' }}>
                <input
                  type="file"
                  id="imgUploadFile"
                  accept="image/*"
                  style={{ display: 'none' }}
                  onChange={async (e) => {
                    const file = e.target.files?.[0];
                    if (!file) return;
                    try {
                      const data = await uploadFile(ENDPOINTS.CATALOG.MEDIA_UPLOAD, file);
                      const url = data?.url || data?.publicUrl;
                      if (url) setNewImageUrl(url);
                    } catch (err) {
                      alert(err.message || 'Lỗi tải ảnh lên');
                    } finally {
                      e.target.value = '';
                    }
                  }}
                />
                <Button
                  type="button"
                  variant="outline"
                  icon={Upload}
                  onClick={() => document.getElementById('imgUploadFile')?.click()}
                >
                  Tải tệp
                </Button>
              </div>
            </div>

            <div className="grid-3">
              <Input
                label="Mô tả ảnh (Alt Text)"
                value={newImageAlt}
                onChange={(e) => setNewImageAlt(e.target.value)}
                placeholder="Mô tả sản phẩm"
              />
              <Input
                label="Thứ tự hiển thị"
                type="number"
                value={newImageOrder}
                onChange={(e) => setNewImageOrder(e.target.value)}
              />
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '24px' }}>
                <input
                  type="checkbox"
                  id="chkPrimary"
                  checked={newImagePrimary}
                  onChange={(e) => setNewImagePrimary(e.target.checked)}
                />
                <label htmlFor="chkPrimary" style={{ fontSize: '13px', fontWeight: 600, cursor: 'pointer' }}>
                  Đặt làm ảnh chính
                </label>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
              <Button variant="outline" type="button" onClick={() => setImagesModalOpen(false)}>Đóng</Button>
              <Button variant="primary" type="submit" loading={imageSubmitting}>Thêm ảnh</Button>
            </div>
          </form>
        </div>
      </Modal>

      {/* MODAL 9: DERMATOLOGY & USAGE GUIDELINES */}
      <Modal
        isOpen={dermaModalOpen}
        onClose={() => setDermaModalOpen(false)}
        title={`Hướng dẫn sử dụng & Quy trình da liễu: ${selectedProductForDerma?.name || ''}`}
        maxWidth="680px"
      >
        <form onSubmit={handleSaveDermaUsage}>
          <div style={{ marginBottom: '12px' }}>
            <Input
              label="Tần suất sử dụng *"
              value={dermaFrequency}
              onChange={(e) => setDermaFrequency(e.target.value)}
              placeholder="VD: 1-2 lần/ngày, Dùng hàng ngày vào buổi sáng..."
              required
            />
          </div>

          <div style={{ marginBottom: '12px' }}>
            <label className="form-label">Thời điểm khuyên dùng (Mỗi dòng một mục)</label>
            <textarea
              className="form-textarea"
              rows={3}
              value={dermaWhenToUse}
              onChange={(e) => setDermaWhenToUse(e.target.value)}
              placeholder="Buổi sáng&#10;Sau bước làm sạch da&#10;Trước khi thoa kem dưỡng"
            />
          </div>

          <div style={{ marginBottom: '12px' }}>
            <label className="form-label">Các bước thực hiện chi tiết (Mỗi dòng một bước)</label>
            <textarea
              className="form-textarea"
              rows={4}
              value={dermaInstructions}
              onChange={(e) => setDermaInstructions(e.target.value)}
              placeholder="Bước 1: Làm sạch da mặt với sữa rửa mặt dịu nhẹ&#10;Bước 2: Lấy 3-4 giọt tinh chất thoa đều lên mặt&#10;Bước 3: Vỗ nhẹ nhàng để dưỡng chất thẩm thấu sâu"
            />
          </div>

          <div style={{ marginBottom: '12px' }}>
            <label className="form-label">Cảnh báo & Lưu ý an toàn (Mỗi dòng một cảnh báo)</label>
            <textarea
              className="form-textarea"
              rows={3}
              value={dermaWarnings}
              onChange={(e) => setDermaWarnings(e.target.value)}
              placeholder="Tránh tiếp xúc trực tiếp với mắt&#10;Bắt buộc dùng kem chống nắng vào ban ngày khi sử dụng sản phẩm chứa BHA/Retinol"
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setDermaModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={dermaSubmitting}>Lưu quy trình da liễu</Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 10: CREATE / EDIT BANNER */}
      <Modal
        isOpen={bannerModalOpen}
        onClose={() => setBannerModalOpen(false)}
        title={editingBanner ? `Cập nhật Banner #${editingBanner.id}` : 'Thêm Banner Quảng Cáo Mới'}
        maxWidth="600px"
      >
        <form onSubmit={handleSubmitBanner}>
          <Input
            label="Tiêu đề banner *"
            value={bannerForm.title}
            onChange={(e) => setBannerForm({ ...bannerForm, title: e.target.value })}
            required
            placeholder="VD: Mỹ Phẩm Chính Hãng 100%"
          />
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input
              label="Huy hiệu / Badge"
              value={bannerForm.badge}
              onChange={(e) => setBannerForm({ ...bannerForm, badge: e.target.value })}
              placeholder="VD: HASAKI DEALS GIỜ VÀNG"
            />
            <Select
              label="Vị trí hiển thị *"
              value={bannerForm.position}
              onChange={(e) => setBannerForm({ ...bannerForm, position: e.target.value })}
              options={[
                { label: 'Slide chính (HERO_SLIDE)', value: 'HERO_SLIDE' },
                { label: 'Banner phụ bên cạnh (HERO_SIDE)', value: 'HERO_SIDE' },
                { label: 'Khuyến mãi khác (PROMO)', value: 'PROMO' },
              ]}
            />
          </div>
          <div style={{ marginTop: '12px' }}>
            <Input
              label="Đường dẫn ảnh banner (URL) *"
              value={bannerForm.imageUrl}
              onChange={(e) => setBannerForm({ ...bannerForm, imageUrl: e.target.value })}
              required
              placeholder="https://images.unsplash.com/..."
            />
          </div>
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input
              label="Liên kết khi click (targetUrl)"
              value={bannerForm.targetUrl}
              onChange={(e) => setBannerForm({ ...bannerForm, targetUrl: e.target.value })}
              placeholder="VD: products, booking, #flash-sale"
            />
            <Input
              label="Chữ nút bấm (CTA)"
              value={bannerForm.ctaText}
              onChange={(e) => setBannerForm({ ...bannerForm, ctaText: e.target.value })}
              placeholder="VD: Mua Sắm Ngay"
            />
          </div>
          <div className="grid-2" style={{ marginTop: '12px' }}>
            <Input
              label="Thứ tự hiển thị"
              type="number"
              value={String(bannerForm.sortOrder)}
              onChange={(e) => setBannerForm({ ...bannerForm, sortOrder: parseInt(e.target.value, 10) || 0 })}
            />
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '26px' }}>
              <input
                type="checkbox"
                id="chkBannerActive"
                checked={bannerForm.isActive}
                onChange={(e) => setBannerForm({ ...bannerForm, isActive: e.target.checked })}
              />
              <label htmlFor="chkBannerActive" style={{ fontSize: '13px', fontWeight: 600, cursor: 'pointer' }}>
                Đang hoạt động (Hiển thị)
              </label>
            </div>
          </div>
          <div style={{ marginTop: '12px' }}>
            <label className="form-label">Mô tả ngắn</label>
            <textarea
              className="form-textarea"
              rows={2}
              value={bannerForm.description}
              onChange={(e) => setBannerForm({ ...bannerForm, description: e.target.value })}
              placeholder="Nội dung tóm tắt trên banner..."
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
            <Button variant="outline" type="button" onClick={() => setBannerModalOpen(false)}>Hủy</Button>
            <Button variant="primary" type="submit" loading={bannerSubmitting}>Lưu banner</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
