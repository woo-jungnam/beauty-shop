import unittest

from app.packaging_match import matches_printed_brand, matches_printed_family, printed_brand, printed_product_family


class PackagingMatchTest(unittest.TestCase):
    def test_loreal_micellar_water_excludes_other_brands_and_product_types(self):
        text = "L’ORÉAL PARIS MICELLAR WATER 3-IN-1"
        key = printed_brand("L'Oréal Paris", text)
        self.assertEqual(key, "loreal")
        family = printed_product_family(text)
        self.assertEqual(family, "micellar_water")
        candidates = [
            {"brand_name": "La Roche-Posay", "product_name": "Sữa tắm Lipikar"},
            {"brand_name": "Bioderma", "product_name": "Kem dưỡng Hydrabio"},
            {"brand_name": "Nivea", "product_name": "Nivea MicellAIR Micellar Water"},
            {"brand_name": "Head & Shoulders", "product_name": "Dầu gội"},
            {"brand_name": "L'Oreal", "product_name": "Dầu gội Elseve"},
            {"brand_name": "L’Oreal Paris", "product_name": "Nước tẩy trang Micellar 400ml"},
        ]
        self.assertEqual(
            [item for item in candidates if matches_printed_brand(item, key) and matches_printed_family(item, family)],
            candidates[-1:],
        )

    def test_missing_or_inconsistent_label_does_not_activate_brand_filter(self):
        self.assertIsNone(printed_brand("L'Oréal", "MICELLAR WATER"))
        self.assertIsNone(printed_brand("MAC", "MACADAMIA OIL"))
        self.assertIsNone(printed_brand("L'Oréal", None))

    def test_other_explicit_product_labels_match_vietnamese_catalog_names(self):
        for label, expected_title, other_title in (
            ("Anti-dandruff shampoo", "Dầu gội trị gàu", "Sữa tắm dưỡng da"),
            ("Aloe shower gel", "Sữa tắm nha đam", "Dầu gội nha đam"),
            ("Daily sunscreen SPF 50", "Kem chống nắng SPF 50", "Kem dưỡng ẩm ban đêm"),
            ("Barrier moisturizer", "Kem dưỡng ẩm bảo vệ da", "Kem chống nắng SPF 50"),
        ):
            with self.subTest(label=label):
                family = printed_product_family(label)
                self.assertIsNotNone(family)
                self.assertTrue(matches_printed_family({"product_name": expected_title}, family))
                self.assertFalse(matches_printed_family({"product_name": other_title}, family))

    def test_ambiguous_or_missing_family_does_not_exclude_products(self):
        self.assertIsNone(printed_product_family("Shampoo and body wash"))
        self.assertIsNone(printed_product_family(None))
        self.assertTrue(matches_printed_family({"product_name": "Anthelios SPF 50"}, "sunscreen"))
