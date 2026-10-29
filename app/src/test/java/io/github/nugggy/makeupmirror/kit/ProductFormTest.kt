package io.github.nugggy.makeupmirror.kit

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductFormTest {
    @Test
    fun defaultNameNumbersProductsOfTheSameType() {
        val kit = listOf(
            Product(1, ProductType.BLUSH, "Rose", 0),
            Product(2, ProductType.BLUSH, "Peach", 0),
            Product(3, ProductType.LIPSTICK, "Red", 0),
        )
        assertEquals("Blush 3", ProductForm.defaultName(ProductType.BLUSH, kit))
        assertEquals("Mascara 1", ProductForm.defaultName(ProductType.MASCARA, kit))
    }
}
