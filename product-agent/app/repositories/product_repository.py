import re
from typing import Any
from sqlalchemy import and_, or_, select
from sqlalchemy.orm import Session
from app.models.database import ProductModel, getDbSession, initDb
from app.schemas.product import ProductCreate, ProductRead, ProductUpdate


class ProductRepository:
    def __init__(self, sessionFactory=getDbSession, **kwargs) -> None:
        self.sessionFactory = kwargs.get("session_factory", sessionFactory)
        self.session_factory = self.sessionFactory
        initDb()

    CATEGORY_IMAGES = {
        "cleanser": "https://images.unsplash.com/photo-1556228720-195a672e8a03?w=500&auto=format&fit=crop&q=80",
        "serum": "https://images.unsplash.com/photo-1620916566398-39f1143ab7be?w=500&auto=format&fit=crop&q=80",
        "sunscreen": "https://images.unsplash.com/photo-1598440947619-2c35fc9aa908?w=500&auto=format&fit=crop&q=80",
        "cream": "https://images.unsplash.com/photo-1570194065650-d99fb4bedf0a?w=500&auto=format&fit=crop&q=80",
        "toner": "https://images.unsplash.com/photo-1608248597359-5777491d90fc?w=500&auto=format&fit=crop&q=80",
        "Mặt nạ": "https://images.unsplash.com/photo-1596755389378-c31d21fd1273?w=500&auto=format&fit=crop&q=80",
        "Dầu gội": "https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d?w=500&auto=format&fit=crop&q=80",
        "Dầu xả": "https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d?w=500&auto=format&fit=crop&q=80",
        "Son môi": "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=500&auto=format&fit=crop&q=80",
        "Dưỡng môi": "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=500&auto=format&fit=crop&q=80",
        "Nước hoa": "https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?w=500&auto=format&fit=crop&q=80",
    }
    DEFAULT_IMAGE = "https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?w=500&auto=format&fit=crop&q=80"

    @classmethod
    def getDefaultImage(cls, category: str | None) -> str:
        if not category:
            return cls.DEFAULT_IMAGE
        cLow = category.strip().lower()
        for catK, img in cls.CATEGORY_IMAGES.items():
            if catK.lower() in cLow or cLow in catK.lower():
                return img
        return cls.DEFAULT_IMAGE

    def toSchema(self, model: ProductModel) -> ProductRead:
        img = getattr(model, "imageUrl", None) or getattr(model, "image_url", None) or self.getDefaultImage(model.category)
        return ProductRead(
            id=model.id,
            name=model.name,
            brand=model.brand,
            category=model.category,
            price=model.price,
            description=model.description,
            ingredients=model.ingredients,
            benefits=model.benefits,
            skinType=model.skin_type,
            concerns=model.concerns,
            usage=model.usage,
            rating=model.rating,
            totalReviews=getattr(model, "totalReviews", 0) or getattr(model, "total_reviews", 0) or 0,
            totalSold=getattr(model, "totalSold", 0) or getattr(model, "total_sold", 0) or 0,
            reviews=model.reviews,
            inStock=getattr(model, "inStock", True) if getattr(model, "inStock", None) is not None else getattr(model, "in_stock", True),
            imageUrl=img,
            targetType=getattr(model, "targetType", "PRODUCT") or getattr(model, "target_type", "PRODUCT") or "PRODUCT",
            durationMinutes=getattr(model, "durationMinutes", 0) or getattr(model, "duration_minutes", 0) or 0,
        )

    def create(self, data: ProductCreate) -> ProductRead:
        session: Session = self.sessionFactory()
        try:
            model = ProductModel(
                id=data.id,
                name=data.name,
                brand=data.brand,
                category=data.category,
                price=data.price,
                description=data.description,
                ingredients=data.ingredients,
                benefits=data.benefits,
                usage=data.usage,
                rating=data.rating,
                totalReviews=data.totalReviews,
                totalSold=data.totalSold,
                inStock=data.inStock,
                imageUrl=data.imageUrl or self.getDefaultImage(data.category),
                targetType=data.targetType or "PRODUCT",
                durationMinutes=data.durationMinutes or 0,
            )
            model.skinType = data.skinType
            model.concerns = data.concerns
            model.reviews = data.reviews

            session.add(model)
            session.commit()
            session.refresh(model)
            return self.toSchema(model)
        finally:
            session.close()

    def createOrUpdate(self, data: ProductCreate) -> ProductRead:
        session: Session = self.sessionFactory()
        try:
            model = session.get(ProductModel, data.id)
            if model is None:
                model = ProductModel(
                    id=data.id,
                    name=data.name,
                    brand=data.brand,
                    category=data.category,
                    price=data.price,
                    description=data.description,
                    ingredients=data.ingredients,
                    benefits=data.benefits,
                    usage=data.usage,
                    rating=data.rating,
                    totalReviews=data.totalReviews,
                    totalSold=data.totalSold,
                    inStock=data.inStock,
                    imageUrl=data.imageUrl or self.getDefaultImage(data.category),
                    targetType=data.targetType or "PRODUCT",
                    durationMinutes=data.durationMinutes or 0,
                )
                session.add(model)
            else:
                model.name = data.name
                model.brand = data.brand
                model.category = data.category
                model.price = data.price
                model.description = data.description
                model.ingredients = data.ingredients
                model.benefits = data.benefits
                model.usage = data.usage
                model.rating = data.rating
                model.totalReviews = data.totalReviews
                model.totalSold = data.totalSold
                model.inStock = data.inStock
                if data.imageUrl:
                    model.imageUrl = data.imageUrl
                if data.targetType:
                    model.targetType = data.targetType
                if data.durationMinutes is not None:
                    model.durationMinutes = data.durationMinutes

            model.skinType = data.skinType
            model.concerns = data.concerns
            model.reviews = data.reviews

            session.commit()
            session.refresh(model)
            return self.toSchema(model)
        finally:
            session.close()

    def getById(self, productId: str, **kwargs) -> ProductRead | None:
        productId = kwargs.get("product_id", productId)
        session: Session = self.sessionFactory()
        try:
            model = session.get(ProductModel, productId)
            return self.toSchema(model) if model else None
        finally:
            session.close()

    def getByIds(self, productIds: list[str], **kwargs) -> list[ProductRead]:
        productIds = kwargs.get("product_ids", productIds)
        if not productIds:
            return []
        session: Session = self.sessionFactory()
        try:
            stmt = select(ProductModel).where(ProductModel.id.in_(productIds))
            models = session.execute(stmt).scalars().all()
            return [self.toSchema(m) for m in models]
        finally:
            session.close()

    def update(self, productId: str, data: ProductUpdate, **kwargs) -> ProductRead | None:
        productId = kwargs.get("product_id", productId)
        session: Session = self.sessionFactory()
        try:
            model = session.get(ProductModel, productId)
            if not model:
                return None

            updateDict = data.model_dump(exclude_unset=True)
            for field, val in updateDict.items():
                if field in ("skin_type", "skinType"):
                    model.skinType = val
                elif field == "concerns":
                    model.concerns = val
                elif field == "reviews":
                    model.reviews = val
                else:
                    setattr(model, field, val)

            session.commit()
            session.refresh(model)
            return self.toSchema(model)
        finally:
            session.close()

    def delete(self, productId: str, **kwargs) -> bool:
        productId = kwargs.get("product_id", productId)
        session: Session = self.sessionFactory()
        try:
            model = session.get(ProductModel, productId)
            if not model:
                return False
            session.delete(model)
            session.commit()
            return True
        finally:
            session.close()

    def listAll(self, limit: int = 100) -> list[ProductRead]:
        session: Session = self.sessionFactory()
        try:
            stmt = select(ProductModel).limit(limit)
            models = session.execute(stmt).scalars().all()
            return [self.toSchema(m) for m in models]
        finally:
            session.close()

    def filterProducts(
        self,
        category: str | None = None,
        brand: str | None = None,
        minPrice: float | None = None,
        maxPrice: float | None = None,
        skinType: str | None = None,
        queryKeyword: str | None = None,
        formFactor: str | None = None,
        targetType: str | None = None,
        inStockOnly: bool = True,
        limit: int = 100,
        **kwargs,
    ) -> list[ProductRead]:
        minPrice = kwargs.get("min_price", minPrice)
        maxPrice = kwargs.get("max_price", maxPrice)
        skinType = kwargs.get("skin_type", skinType)
        queryKeyword = kwargs.get("query_keyword", queryKeyword)
        formFactor = kwargs.get("form_factor", formFactor)
        targetType = kwargs.get("target_type", targetType)
        inStockOnly = kwargs.get("in_stock_only", inStockOnly)

        session: Session = self.sessionFactory()
        try:
            conditions = []

            if targetType:
                conditions.append(ProductModel.targetType == targetType)

            if inStockOnly:
                conditions.append(ProductModel.inStock == True)

            if category:
                conditions.append(ProductModel.category.ilike(category.strip()))

            if brand:
                conditions.append(ProductModel.brand.ilike(brand.strip()))

            if minPrice is not None:
                conditions.append(ProductModel.price >= minPrice)

            if maxPrice is not None:
                conditions.append(ProductModel.price <= maxPrice)

            if skinType:
                cleanSkin = skinType.strip().upper()
                conditions.append(
                    or_(
                        ProductModel._skin_type.ilike(f"%{cleanSkin}%"),
                        ProductModel._skin_type.ilike("%ALL%"),
                        ProductModel._skin_type.ilike("%MỌI LOẠI DA%"),
                    )
                )

            if queryKeyword:
                kw = queryKeyword.strip()
                conditions.append(
                    or_(
                        ProductModel.name.ilike(f"%{kw}%"),
                        ProductModel.brand.ilike(f"%{kw}%"),
                        ProductModel.description.ilike(f"%{kw}%"),
                    )
                )

            stmt = select(ProductModel).where(and_(*conditions))

            stmt = stmt.order_by(
                ProductModel.rating.desc(),
                ProductModel.totalReviews.desc(),
                ProductModel.price.asc(),
            ).limit(limit)

            models = session.execute(stmt).scalars().all()

            diverseProds: list[ProductRead] = []
            seenBase: dict[str, int] = {}
            for m in models:
                pRead = self.toSchema(m)
                cleanBase = re.sub(r"mã\s*\d+", "", pRead.name.lower()).strip()
                baseK = f"{pRead.brand.lower()}:{cleanBase}"
                if seenBase.get(baseK, 0) < 2:
                    diverseProds.append(pRead)
                    seenBase[baseK] = seenBase.get(baseK, 0) + 1
                if len(diverseProds) >= limit:
                    break
            return diverseProds
        finally:
            session.close()

    get_default_image = getDefaultImage
    _to_schema = toSchema
    create_or_update = createOrUpdate
    get_by_id = getById
    get_by_ids = getByIds
    list_all = listAll
    filter_products = filterProducts


productRepository = ProductRepository()
product_repository = productRepository
