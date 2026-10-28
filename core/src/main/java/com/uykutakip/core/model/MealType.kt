package com.uykutakip.core.model

enum class MealType(val label: String, val isMainMeal: Boolean, val isEating: Boolean) {
    BREAKFAST("Kahvaltı", true, true),
    LUNCH("Öğle Yemeği", true, true),
    DINNER("Akşam Yemeği", true, true),
    SNACK("Atıştırma", false, true),
    DRINK("İçecek", false, false);

    companion object {
        fun fromLabel(label: String): MealType = entries.firstOrNull { it.label == label } ?: SNACK
    }
}

