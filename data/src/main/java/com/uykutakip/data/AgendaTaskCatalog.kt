package com.uykutakip.data

data class AgendaTaskTemplate(
    val id: String,
    val text: String,
    val category: String = "",
    val subcategory: String = "",
    val favorite: Boolean = false,
    val builtIn: Boolean = false,
)

data class AgendaCategoryOption(
    val id: Long,
    val category: String,
    val subcategory: String = "",
)

data class AgendaTaskCatalog(
    val templates: List<AgendaTaskTemplate> = emptyList(),
    val categories: List<AgendaCategoryOption> = emptyList(),
)
