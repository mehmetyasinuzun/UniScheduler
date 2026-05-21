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
//
// Defansif tasarım:
//   • Boş items → text boşaltılır, selectedPosition() 0 döner, selectedItem() null
//   • Klavye/IME açılmaz (setShowSoftInputOnFocus=false) — bazı cihazlarda
//     keyListener=null yetmiyor, soft keyboard yine pop-up oluyordu
//   • setItems çağrılınca yeni listte eski pozisyon clamp edilir (out-of-range crash yok)
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
        // IME açılmasını engelle — Spinner gibi davransın. Sadece keyListener=null
        // bazı OEM cihazlarda yetmiyor (Samsung One UI focus'ta klavyeyi çağırıyor),
        // setShowSoftInputOnFocus + setTextIsSelectable(false) kombinasyonu güvenli.
        view.inputType = android.text.InputType.TYPE_NULL
        view.keyListener = null
        view.setTextIsSelectable(false)
        view.setShowSoftInputOnFocus(false)
        view.isCursorVisible = false

        view.setOnClickListener { view.showDropDown() }
        view.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) view.showDropDown() }
        setItems(items)
    }

    /**
     * Yeni item listesi yükler. Eski selectedPos yeni list boyutunu aşarsa
     * 0'a clamp edilir — pozisyon tutarlılığı kullanıcıdan gizli kalır.
     *
     * @param initialPosition negatif veya boyut dışıysa 0 olarak yorumlanır.
     */
    fun setItems(items: List<T>, initialPosition: Int = -1) {
        currentItems = items
        val labels = items.map(labelOf)
        // simple_list_item_1 dropdown için Material list satırı standardı.
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
            val desired = if (initialPosition >= 0) initialPosition else selectedPos
            val pos = desired.coerceIn(0, items.lastIndex)
            selectedPos = pos
            view.setText(labels[pos], /* filter = */ false)
        } else {
            // Boş list — text temizlenir, ama klavye/dropdown durumuna dokunmayız.
            selectedPos = 0
            view.setText("", /* filter = */ false)
        }
    }

    /**
     * Aktif seçimin pozisyonu. Liste boşsa veya hiç tıklanmadıysa 0 döner —
     * çağıran kod yine de `currentItems.getOrNull()` ile güvende erişmeli.
     */
    fun selectedPosition(): Int =
        if (currentItems.isEmpty()) 0
        else selectedPos.coerceIn(0, currentItems.lastIndex)

    /** Boş listte null döner; çağıran kod null kontrolü yapmalı. */
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
