package io.github.nugggy.makeupmirror.kit

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class ProductType(val label: String) {
    FOUNDATION("Foundation"),
    CONCEALER("Concealer"),
    POWDER("Powder"),
    BLUSH("Blush"),
    BRONZER("Bronzer"),
    CONTOUR("Contour"),
    HIGHLIGHTER("Highlighter"),
    EYESHADOW("Eyeshadow"),
    EYELINER("Eyeliner"),
    MASCARA("Mascara"),
    BROW("Brow pencil"),
    LIPSTICK("Lipstick"),
    LIP_GLOSS("Lip gloss"),
    LIP_LINER("Lip liner"),
}

enum class Finish(val label: String) {
    MATTE("Matte"),
    SATIN("Satin"),
    SHIMMER("Shimmer"),
    GLOSS("Gloss"),
}

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ProductType,
    val name: String,
    val colour: Int,
    val finish: Finish = Finish.MATTE,
    val createdAt: Long = System.currentTimeMillis(),
)
