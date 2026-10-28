package com.uykutakip.core.model

data class FoodEntry(
    val name: String,
    val quantity: Double,
    val unit: FoodUnit,
    val category: FoodCategory,
)

enum class FoodUnit(val label: String) {
    PIECE("adet"),
    SLICE("dilim"),
    GRAM("g"),
    MILLILITER("ml"),
    CUP("fincan"),
    GLASS("bardak"),
    BOWL("kase"),
    PLATE("tabak"),
    PORTION("porsiyon"),
    SPOON("kaşık"),
    TEASPOON("çay kaşığı"),
    TABLESPOON("yemek kaşığı"),
    PACKAGE("paket"),
    CAN("kutu"),
}

enum class FoodCategory(val label: String) {
    BREAKFAST("Kahvaltılık"),
    SOUP("Çorba"),
    MEAT("Et"),
    POULTRY("Tavuk"),
    SEAFOOD("Balık ve deniz ürünü"),
    LEGUME("Bakliyat"),
    GRAIN("Tahıl ve pilav"),
    PASTA("Makarna"),
    VEGETABLE("Sebze"),
    SALAD("Salata"),
    FRUIT("Meyve"),
    DAIRY("Süt ürünü"),
    BAKERY("Ekmek ve hamur işi"),
    FAST_FOOD("Fast food"),
    DESSERT("Tatlı"),
    SNACK("Atıştırmalık"),
    NUT("Kuruyemiş"),
    DRINK("İçecek"),
    OTHER("Diğer"),
}
