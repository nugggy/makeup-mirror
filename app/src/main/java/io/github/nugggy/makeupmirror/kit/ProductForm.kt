package io.github.nugggy.makeupmirror.kit

object ProductForm {
    /** Name used when the user leaves the name blank, e.g. "Blush 3". */
    fun defaultName(type: ProductType, existing: List<Product>): String =
        "${type.label} ${existing.count { it.type == type } + 1}"
}
