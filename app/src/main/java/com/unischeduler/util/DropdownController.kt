// Material 3 ExposedDropdownMenu için ince bir kontrolcü.
//
// Spinner'dan ExposedDropdownMenu'ye (MaterialAutoCompleteTextView + TextInputLayout)
// geçişte API farkını gizler. Spinner'da `selectedItemPosition` / `setSelection`
// kullanılıyordu — burada aynı semantiği item-listesi üzerinden pozisyon takibi
// ile koruruz. Sonuç olarak Fragment kodunda minimum değişiklikle çağrı yapılır:
//
//   val ctl = DropdownController(binding.actvDay, days)
//   ctl.selectedItem()           // current selection (Day enum, etc.)
//   ctl.selectedPosition()       // current index, 0 if hiç tıklanmadıysa
//   ctl.setSelection(2)          // programmatically seç
//   ctl.onSelected { item, pos } // selection callback
package com.unischeduler.util

import android.widget.ArrayAdapter
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class DropdownController<T>(
    private val view: MaterialAutoCompleteTextView,
    items: List<T>,
    private val labelOf: (T) -> String = { it.toString() }
) {
    private var currentItems: List<T> = emptyList()
    private var selectedPos: Int = 0
    private var onSelected: ((T, Int) -> Unit)? = null

    init {
        view.inputType = android.text.InputType.TYPE_NULL
        // Klavye/IME açılmasını engelle — Spinner gibi davransın.
        view.keyListener = null
        view.setOnClickListener { view.showDropDown() }
        view.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) view.showDropDown() }
        setItems(items)
    }

    fun setItems(items: List<T>, initialPosition: Int = 0) {
        currentItems = items
        val labels = items.map(labelOf)
        // simple_spinner_dropdown_item Material attribute'larını alır,
        // simple_list_item_1 daha kompakt durur — dropdown için ikincisi.
        val adapter = ArrayAdapter(
            view.context,
            android.R.layout.simple_list_item_1,
            labels
        )
        view.setAdapter(adapter)
        view.setOnItemClickListener { _, _, pos, _ ->
            selectedPos = pos
            currentItems.getOrNull(pos)?.let { onSelected?.invoke(it, pos) }
        }
        if (items.isNotEmpty()) {
            val pos = initialPosition.coerceIn(0, items.lastIndex)
            selectedPos = pos
            view.setText(labels[pos], /* filter = */ false)
        } else {
            selectedPos = 0
            view.setText("", false)
        }
    }

    fun selectedPosition(): Int = selectedPos.coerceIn(0, (currentItems.size - 1).coerceAtLeast(0))

    fun selectedItem(): T? = currentItems.getOrNull(selectedPosition())

    fun setSelection(pos: Int) {
        if (currentItems.isEmpty()) return
        val clamped = pos.coerceIn(0, currentItems.lastIndex)
        selectedPos = clamped
        view.setText(labelOf(currentItems[clamped]), /* filter = */ false)
    }

    fun onSelected(block: (T, Int) -> Unit) {
        onSelected = block
    }

    fun items(): List<T> = currentItems
}
