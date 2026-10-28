package com.uykutakip.feature.nutrition

import com.uykutakip.core.model.FoodCategory
import com.uykutakip.core.model.FoodUnit
import com.uykutakip.core.model.MealType

internal data class FoodCatalogItem(
    val name: String,
    val category: FoodCategory,
    val defaultQuantity: Double,
    val defaultUnit: FoodUnit,
)

internal object FoodCatalog {
    val items = listOf(
        food("Yumurta", FoodCategory.BREAKFAST, 1.0, FoodUnit.PIECE),
        food("Haşlanmış yumurta", FoodCategory.BREAKFAST, 1.0, FoodUnit.PIECE),
        food("Omlet", FoodCategory.BREAKFAST),
        food("Menemen", FoodCategory.BREAKFAST),
        food("Beyaz peynir", FoodCategory.BREAKFAST, 30.0, FoodUnit.GRAM),
        food("Kaşar peyniri", FoodCategory.BREAKFAST, 30.0, FoodUnit.GRAM),
        food("Lor peyniri", FoodCategory.BREAKFAST, 50.0, FoodUnit.GRAM),
        food("Zeytin", FoodCategory.BREAKFAST, 5.0, FoodUnit.PIECE),
        food("Bal", FoodCategory.BREAKFAST, 1.0, FoodUnit.TABLESPOON),
        food("Reçel", FoodCategory.BREAKFAST, 1.0, FoodUnit.TABLESPOON),
        food("Tahin pekmez", FoodCategory.BREAKFAST, 1.0, FoodUnit.TABLESPOON),
        food("Tereyağı", FoodCategory.BREAKFAST, 10.0, FoodUnit.GRAM),
        food("Sucuk", FoodCategory.BREAKFAST, 50.0, FoodUnit.GRAM),
        food("Sosis", FoodCategory.BREAKFAST, 2.0, FoodUnit.PIECE),
        food("Mısır gevreği", FoodCategory.BREAKFAST, 1.0, FoodUnit.BOWL),

        food("Mercimek çorbası", FoodCategory.SOUP, 1.0, FoodUnit.BOWL),
        food("Ezogelin çorbası", FoodCategory.SOUP, 1.0, FoodUnit.BOWL),
        food("Tarhana çorbası", FoodCategory.SOUP, 1.0, FoodUnit.BOWL),
        food("Domates çorbası", FoodCategory.SOUP, 1.0, FoodUnit.BOWL),
        food("Tavuk çorbası", FoodCategory.SOUP, 1.0, FoodUnit.BOWL),
        food("Yayla çorbası", FoodCategory.SOUP, 1.0, FoodUnit.BOWL),

        food("Köfte", FoodCategory.MEAT, 4.0, FoodUnit.PIECE),
        food("Dana eti", FoodCategory.MEAT, 150.0, FoodUnit.GRAM),
        food("Et sote", FoodCategory.MEAT),
        food("Kavurma", FoodCategory.MEAT, 150.0, FoodUnit.GRAM),
        food("Kuzu eti", FoodCategory.MEAT, 150.0, FoodUnit.GRAM),
        food("Biftek", FoodCategory.MEAT, 1.0, FoodUnit.PIECE),
        food("Döner", FoodCategory.MEAT, 1.0, FoodUnit.PORTION),

        food("Tavuk göğsü", FoodCategory.POULTRY, 150.0, FoodUnit.GRAM),
        food("Izgara tavuk", FoodCategory.POULTRY),
        food("Tavuk sote", FoodCategory.POULTRY),
        food("Tavuk şiş", FoodCategory.POULTRY, 1.0, FoodUnit.PORTION),
        food("Hindi eti", FoodCategory.POULTRY, 150.0, FoodUnit.GRAM),

        food("Somon", FoodCategory.SEAFOOD, 150.0, FoodUnit.GRAM),
        food("Levrek", FoodCategory.SEAFOOD, 1.0, FoodUnit.PIECE),
        food("Çipura", FoodCategory.SEAFOOD, 1.0, FoodUnit.PIECE),
        food("Ton balığı", FoodCategory.SEAFOOD, 1.0, FoodUnit.CAN),
        food("Hamsi", FoodCategory.SEAFOOD, 1.0, FoodUnit.PORTION),
        food("Karides", FoodCategory.SEAFOOD, 1.0, FoodUnit.PORTION),

        food("Kuru fasulye", FoodCategory.LEGUME, 1.0, FoodUnit.PLATE),
        food("Nohut yemeği", FoodCategory.LEGUME, 1.0, FoodUnit.PLATE),
        food("Yeşil mercimek", FoodCategory.LEGUME, 1.0, FoodUnit.PLATE),
        food("Barbunya", FoodCategory.LEGUME, 1.0, FoodUnit.PLATE),
        food("Humus", FoodCategory.LEGUME, 3.0, FoodUnit.TABLESPOON),

        food("Pirinç pilavı", FoodCategory.GRAIN, 1.0, FoodUnit.PLATE),
        food("Bulgur pilavı", FoodCategory.GRAIN, 1.0, FoodUnit.PLATE),
        food("Kinoa", FoodCategory.GRAIN, 1.0, FoodUnit.BOWL),
        food("Yulaf", FoodCategory.GRAIN, 50.0, FoodUnit.GRAM),
        food("Patates", FoodCategory.GRAIN, 1.0, FoodUnit.PIECE),
        food("Patates püresi", FoodCategory.GRAIN, 1.0, FoodUnit.PORTION),

        food("Makarna", FoodCategory.PASTA, 1.0, FoodUnit.PLATE),
        food("Spagetti", FoodCategory.PASTA, 1.0, FoodUnit.PLATE),
        food("Mantı", FoodCategory.PASTA, 1.0, FoodUnit.PLATE),
        food("Erişte", FoodCategory.PASTA, 1.0, FoodUnit.PLATE),
        food("Lazanya", FoodCategory.PASTA, 1.0, FoodUnit.PORTION),

        food("Sebze yemeği", FoodCategory.VEGETABLE, 1.0, FoodUnit.PLATE),
        food("Ispanak", FoodCategory.VEGETABLE, 1.0, FoodUnit.PLATE),
        food("Brokoli", FoodCategory.VEGETABLE, 1.0, FoodUnit.PORTION),
        food("Karnabahar", FoodCategory.VEGETABLE, 1.0, FoodUnit.PORTION),
        food("Kabak yemeği", FoodCategory.VEGETABLE, 1.0, FoodUnit.PLATE),
        food("Taze fasulye", FoodCategory.VEGETABLE, 1.0, FoodUnit.PLATE),
        food("Dolma", FoodCategory.VEGETABLE, 4.0, FoodUnit.PIECE),
        food("Patlıcan yemeği", FoodCategory.VEGETABLE, 1.0, FoodUnit.PLATE),

        food("Mevsim salata", FoodCategory.SALAD, 1.0, FoodUnit.BOWL),
        food("Çoban salata", FoodCategory.SALAD, 1.0, FoodUnit.BOWL),
        food("Sezar salata", FoodCategory.SALAD, 1.0, FoodUnit.BOWL),
        food("Ton balıklı salata", FoodCategory.SALAD, 1.0, FoodUnit.BOWL),
        food("Yoğurtlu salata", FoodCategory.SALAD, 1.0, FoodUnit.BOWL),

        food("Elma", FoodCategory.FRUIT, 1.0, FoodUnit.PIECE),
        food("Muz", FoodCategory.FRUIT, 1.0, FoodUnit.PIECE),
        food("Portakal", FoodCategory.FRUIT, 1.0, FoodUnit.PIECE),
        food("Mandalina", FoodCategory.FRUIT, 1.0, FoodUnit.PIECE),
        food("Armut", FoodCategory.FRUIT, 1.0, FoodUnit.PIECE),
        food("Şeftali", FoodCategory.FRUIT, 1.0, FoodUnit.PIECE),
        food("Üzüm", FoodCategory.FRUIT, 150.0, FoodUnit.GRAM),
        food("Çilek", FoodCategory.FRUIT, 150.0, FoodUnit.GRAM),
        food("Karpuz", FoodCategory.FRUIT, 1.0, FoodUnit.SLICE),
        food("Kavun", FoodCategory.FRUIT, 1.0, FoodUnit.SLICE),
        food("Kivi", FoodCategory.FRUIT, 1.0, FoodUnit.PIECE),

        food("Yoğurt", FoodCategory.DAIRY, 1.0, FoodUnit.BOWL),
        food("Süzme yoğurt", FoodCategory.DAIRY, 1.0, FoodUnit.BOWL),
        food("Süt", FoodCategory.DAIRY, 1.0, FoodUnit.GLASS),
        food("Kefir", FoodCategory.DAIRY, 1.0, FoodUnit.GLASS),
        food("Ayran", FoodCategory.DAIRY, 1.0, FoodUnit.GLASS),

        food("Beyaz ekmek", FoodCategory.BAKERY, 1.0, FoodUnit.SLICE),
        food("Tam buğday ekmeği", FoodCategory.BAKERY, 1.0, FoodUnit.SLICE),
        food("Çavdar ekmeği", FoodCategory.BAKERY, 1.0, FoodUnit.SLICE),
        food("Simit", FoodCategory.BAKERY, 1.0, FoodUnit.PIECE),
        food("Poğaça", FoodCategory.BAKERY, 1.0, FoodUnit.PIECE),
        food("Börek", FoodCategory.BAKERY, 1.0, FoodUnit.SLICE),
        food("Lavaş", FoodCategory.BAKERY, 1.0, FoodUnit.PIECE),
        food("Bazlama", FoodCategory.BAKERY, 1.0, FoodUnit.PIECE),

        food("Hamburger", FoodCategory.FAST_FOOD, 1.0, FoodUnit.PIECE),
        food("Pizza", FoodCategory.FAST_FOOD, 2.0, FoodUnit.SLICE),
        food("Patates kızartması", FoodCategory.FAST_FOOD, 1.0, FoodUnit.PORTION),
        food("Tost", FoodCategory.FAST_FOOD, 1.0, FoodUnit.PIECE),
        food("Sandviç", FoodCategory.FAST_FOOD, 1.0, FoodUnit.PIECE),
        food("Dürüm", FoodCategory.FAST_FOOD, 1.0, FoodUnit.PIECE),
        food("Lahmacun", FoodCategory.FAST_FOOD, 1.0, FoodUnit.PIECE),
        food("Pide", FoodCategory.FAST_FOOD, 1.0, FoodUnit.PORTION),

        food("Baklava", FoodCategory.DESSERT, 2.0, FoodUnit.PIECE),
        food("Sütlaç", FoodCategory.DESSERT, 1.0, FoodUnit.BOWL),
        food("Kazandibi", FoodCategory.DESSERT, 1.0, FoodUnit.PORTION),
        food("Kek", FoodCategory.DESSERT, 1.0, FoodUnit.SLICE),
        food("Kurabiye", FoodCategory.DESSERT, 2.0, FoodUnit.PIECE),
        food("Dondurma", FoodCategory.DESSERT, 2.0, FoodUnit.SPOON),
        food("Çikolata", FoodCategory.DESSERT, 30.0, FoodUnit.GRAM),

        food("Bisküvi", FoodCategory.SNACK, 2.0, FoodUnit.PIECE),
        food("Kraker", FoodCategory.SNACK, 1.0, FoodUnit.PACKAGE),
        food("Cips", FoodCategory.SNACK, 1.0, FoodUnit.PACKAGE),
        food("Protein bar", FoodCategory.SNACK, 1.0, FoodUnit.PIECE),
        food("Mısır", FoodCategory.SNACK, 1.0, FoodUnit.BOWL),

        food("Badem", FoodCategory.NUT, 30.0, FoodUnit.GRAM),
        food("Ceviz", FoodCategory.NUT, 3.0, FoodUnit.PIECE),
        food("Fındık", FoodCategory.NUT, 30.0, FoodUnit.GRAM),
        food("Antep fıstığı", FoodCategory.NUT, 30.0, FoodUnit.GRAM),
        food("Kaju", FoodCategory.NUT, 30.0, FoodUnit.GRAM),
        food("Ay çekirdeği", FoodCategory.NUT, 1.0, FoodUnit.PACKAGE),

        food("Su", FoodCategory.DRINK, 250.0, FoodUnit.MILLILITER),
        food("Çay", FoodCategory.DRINK, 1.0, FoodUnit.GLASS),
        food("Türk kahvesi", FoodCategory.DRINK, 1.0, FoodUnit.CUP),
        food("Filtre kahve", FoodCategory.DRINK, 1.0, FoodUnit.CUP),
        food("Espresso", FoodCategory.DRINK, 1.0, FoodUnit.CUP),
        food("Latte", FoodCategory.DRINK, 1.0, FoodUnit.GLASS),
        food("Meyve suyu", FoodCategory.DRINK, 1.0, FoodUnit.GLASS),
        food("Soda", FoodCategory.DRINK, 1.0, FoodUnit.CAN),
        food("Gazlı içecek", FoodCategory.DRINK, 1.0, FoodUnit.CAN),
        food("Enerji içeceği", FoodCategory.DRINK, 1.0, FoodUnit.CAN),
    ).sortedWith(compareBy<FoodCatalogItem> { it.category.ordinal }.thenBy { it.name })

    fun suggestions(type: MealType): List<FoodCatalogItem> = when (type) {
        MealType.BREAKFAST -> items.filter { it.category in setOf(FoodCategory.BREAKFAST, FoodCategory.BAKERY, FoodCategory.DRINK) }
        MealType.LUNCH, MealType.DINNER -> items.filter {
            it.category in setOf(
                FoodCategory.SOUP,
                FoodCategory.MEAT,
                FoodCategory.POULTRY,
                FoodCategory.LEGUME,
                FoodCategory.GRAIN,
                FoodCategory.VEGETABLE,
                FoodCategory.SALAD,
            )
        }
        MealType.SNACK -> items.filter {
            it.category in setOf(FoodCategory.FRUIT, FoodCategory.DAIRY, FoodCategory.DESSERT, FoodCategory.SNACK, FoodCategory.NUT)
        }
        MealType.DRINK -> items.filter { it.category == FoodCategory.DRINK }
    }.take(10)
}

private fun food(
    name: String,
    category: FoodCategory,
    quantity: Double = 1.0,
    unit: FoodUnit = FoodUnit.PORTION,
) = FoodCatalogItem(name, category, quantity, unit)
