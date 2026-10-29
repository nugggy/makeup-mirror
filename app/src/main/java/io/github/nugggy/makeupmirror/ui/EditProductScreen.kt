package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.kit.Finish
import io.github.nugggy.makeupmirror.kit.Product
import io.github.nugggy.makeupmirror.kit.ProductForm
import io.github.nugggy.makeupmirror.kit.ProductType
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProductScreen(graph: AppGraph, productId: Long?, onDone: () -> Unit) {
    val dao = graph.db.products()
    val scope = rememberCoroutineScope()
    val kit by dao.observeAll().collectAsStateWithLifecycle(emptyList())
    var existing by remember { mutableStateOf<Product?>(null) }
    var loaded by remember { mutableStateOf(productId == null) }
    var type by rememberSaveable { mutableStateOf<ProductType?>(null) }
    var colour by rememberSaveable { mutableIntStateOf(0xFFC48A7A.toInt()) }
    var name by rememberSaveable { mutableStateOf("") }
    var finish by rememberSaveable { mutableStateOf(Finish.MATTE) }
    var sampling by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(productId) {
        if (productId != null) {
            dao.get(productId)?.let {
                existing = it
                type = it.type
                colour = it.colour
                name = it.name
                finish = it.finish
            }
        }
        loaded = true
    }
    if (!loaded) return
    if (sampling) {
        SwatchSampler(onCapture = { colour = it; sampling = false }, onCancel = { sampling = false })
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (existing == null) "Add product" else "Edit product", style = MaterialTheme.typography.headlineSmall)
        Text("What is it?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProductType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
        }
        val chosen = type ?: return@Column

        Text("Shade", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color(colour))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(16.dp))
            Button(onClick = { sampling = true }) { Text("Capture with camera") }
        }
        Text("Fine-tune the shade if the camera didn't get it quite right:", style = MaterialTheme.typography.bodySmall)
        ColourPicker(colour) { colour = it }

        Text("Finish", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Finish.entries.forEach { f -> FilterChip(selected = finish == f, onClick = { finish = f }, label = { Text(f.label) }) }
        }

        val defaultName = ProductForm.defaultName(chosen, kit.filter { it.id != existing?.id })
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            placeholder = { Text(defaultName) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            existing?.let { product ->
                OutlinedButton(onClick = { scope.launch { dao.delete(product); onDone() } }) { Text("Delete") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Cancel") }
            Button(onClick = {
                scope.launch {
                    dao.upsert(
                        Product(
                            id = existing?.id ?: 0,
                            type = chosen,
                            name = name.trim().ifEmpty { defaultName },
                            colour = colour,
                            finish = finish,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                    onDone()
                }
            }) { Text("Save") }
        }
    }
}
