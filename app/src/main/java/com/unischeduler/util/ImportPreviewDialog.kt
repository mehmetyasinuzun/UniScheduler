// ImportPreviewDialog — reusable dialog for picking which Excel/CSV rows to import.
// Shows each parsed row with a checkbox; user selects what to keep before committing.
// Replaces the old "found N rows, import?" yes/no flow which forced all-or-nothing.
package com.unischeduler.util

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.unischeduler.databinding.DialogImportPreviewBinding
import com.unischeduler.databinding.ItemImportPreviewBinding

object ImportPreviewDialog {

    /**
     * Show a dialog letting the user pick a subset of rows to import.
     *
     * @param context              activity/fragment context
     * @param title                dialog title (e.g. "Dersleri İçe Aktar")
     * @param targetDescription    short text describing where the rows will go (e.g. "Bölüm: Yazılım Mühendisliği")
     * @param rows                 parsed rows from CsvImporter / ExcelHelper
     * @param errors               parse errors (rows skipped because they were malformed)
     * @param titleProvider        renders the bold first line per row
     * @param subtitleProvider     renders the secondary grey line per row
     * @param onImport             invoked with the user's selection when they tap "Seçilenleri İçe Aktar"
     */
    fun <T> show(
        context: Context,
        title: String,
        targetDescription: String,
        rows: List<T>,
        errors: List<String>,
        titleProvider: (T) -> String,
        subtitleProvider: (T) -> String,
        onImport: (List<T>) -> Unit
    ) {
        val inflater = LayoutInflater.from(context)
        val binding  = DialogImportPreviewBinding.inflate(inflater)

        binding.tvImportSummary.text = context.getString(com.unischeduler.R.string.import_preview_found_rows, rows.size)
        binding.tvImportTarget.text  = targetDescription

        if (errors.isNotEmpty()) {
            val firstFew = errors.take(5).joinToString("\n") { "• $it" }
            val more     = if (errors.size > 5) "\n…ve ${errors.size - 5} hata daha" else ""
            binding.tvImportErrors.text       = "${errors.size} satır atlandı:\n$firstFew$more"
            binding.tvImportErrors.visibility = android.view.View.VISIBLE
        }

        val selected = HashSet<Int>().apply { addAll(rows.indices) } // default: all selected

        val adapter = PreviewAdapter(
            rows = rows,
            selected = selected,
            titleProvider = titleProvider,
            subtitleProvider = subtitleProvider,
            onChanged = { binding.tvImportSelectedCount.text = context.getString(com.unischeduler.R.string.import_preview_selected_count, selected.size, rows.size) }
        )
        binding.rvImportPreview.layoutManager = LinearLayoutManager(context)
        binding.rvImportPreview.adapter = adapter
        binding.tvImportSelectedCount.text = context.getString(com.unischeduler.R.string.import_preview_selected_count, selected.size, rows.size)

        binding.btnImportSelectAll.setOnClickListener {
            selected.clear()
            selected.addAll(rows.indices)
            adapter.notifyDataSetChanged()
            binding.tvImportSelectedCount.text = context.getString(com.unischeduler.R.string.import_preview_selected_count, selected.size, rows.size)
        }
        binding.btnImportSelectNone.setOnClickListener {
            selected.clear()
            adapter.notifyDataSetChanged()
            binding.tvImportSelectedCount.text = context.getString(com.unischeduler.R.string.import_preview_selected_count, 0, rows.size)
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(context.getString(com.unischeduler.R.string.import_preview_import_button)) { _, _ ->
                if (selected.isEmpty()) return@setPositiveButton
                val chosen = rows.filterIndexed { i, _ -> i in selected }
                onImport(chosen)
            }
            .setNegativeButton(context.getString(com.unischeduler.R.string.common_cancel), null)
            .show()
    }

    private class PreviewAdapter<T>(
        private val rows: List<T>,
        private val selected: HashSet<Int>,
        private val titleProvider: (T) -> String,
        private val subtitleProvider: (T) -> String,
        private val onChanged: () -> Unit
    ) : RecyclerView.Adapter<PreviewAdapter.VH>() {

        class VH(val binding: ItemImportPreviewBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemImportPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = rows[position]
            holder.binding.tvImportTitle.text    = titleProvider(row)
            holder.binding.tvImportSubtitle.text = subtitleProvider(row)
            holder.binding.cbImportRow.setOnCheckedChangeListener(null)
            holder.binding.cbImportRow.isChecked = position in selected
            holder.binding.cbImportRow.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(position) else selected.remove(position)
                onChanged()
            }
            holder.binding.root.setOnClickListener {
                holder.binding.cbImportRow.isChecked = !holder.binding.cbImportRow.isChecked
            }
        }
    }
}
