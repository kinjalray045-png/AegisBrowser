package com.aegis.browser.ui

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aegis.browser.R
import com.aegis.browser.databinding.ItemListRowBinding
import com.aegis.browser.databinding.SheetListBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

data class ListRow(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val leadingText: String? = null,
    val leadingIcon: Int? = null,
    val actionIcon: Int = R.drawable.ic_close,
    val actionDescription: String? = null,
    val onAction: (() -> Unit)? = null,
    val onClick: () -> Unit
)

class ListRowAdapter(
    private val onClick: (ListRow) -> Unit,
    private val onAction: (ListRow) -> Unit
) : RecyclerView.Adapter<ListRowAdapter.Holder>() {

    private var items: List<ListRow> = emptyList()

    fun submit(rows: List<ListRow>) {
        items = rows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemListRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(private val binding: ItemListRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ListRow) {
            binding.rowTitle.text = row.title
            if (row.subtitle.isNullOrBlank()) {
                binding.rowSubtitle.visibility = View.GONE
            } else {
                binding.rowSubtitle.visibility = View.VISIBLE
                binding.rowSubtitle.text = row.subtitle
            }

            if (row.leadingIcon != null) {
                binding.rowIcon.visibility = View.VISIBLE
                binding.rowLeading.visibility = View.GONE
                binding.rowIcon.setImageResource(row.leadingIcon)
            } else {
                binding.rowIcon.visibility = View.GONE
                binding.rowLeading.visibility = View.VISIBLE
                binding.rowLeading.text = row.leadingText.orEmpty()
            }

            if (row.onAction == null) {
                binding.rowAction.visibility = View.GONE
            } else {
                binding.rowAction.visibility = View.VISIBLE
                binding.rowAction.setIconResource(row.actionIcon)
                row.actionDescription?.let { binding.rowAction.contentDescription = it }
                binding.rowAction.setOnClickListener { onAction(row) }
            }

            binding.root.setOnClickListener { onClick(row) }
        }
    }
}

class ListSheet(
    private val activity: Activity,
    private val title: String,
    private val searchHint: String?,
    private val emptyText: String,
    private val actionLabel: String? = null,
    private val onAction: (() -> Unit)? = null,
    private val rowsProvider: (String) -> List<ListRow>
) {

    private val dialog = BottomSheetDialog(activity)
    private val binding = SheetListBinding.inflate(activity.layoutInflater)
    private val adapter = ListRowAdapter(
        onClick = { row ->
            row.onClick()
            dialog.dismiss()
        },
        onAction = { row ->
            row.onAction?.invoke()
            refresh()
        }
    )
    private var query: String = ""

    fun show() {
        binding.listTitle.text = title
        binding.listRecycler.layoutManager = LinearLayoutManager(activity)
        binding.listRecycler.adapter = adapter
        binding.listEmpty.text = emptyText

        if (actionLabel != null && onAction != null) {
            binding.listAction.visibility = View.VISIBLE
            binding.listAction.text = actionLabel
            binding.listAction.setOnClickListener { onAction() }
        }

        if (searchHint != null) {
            binding.listSearchBox.visibility = View.VISIBLE
            binding.listSearchBox.hint = searchHint
            binding.listSearchInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty()
                    refresh()
                }
            })
        }

        dialog.setContentView(binding.root)
        dialog.setOnShowListener {
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
        refresh()
    }

    fun refresh() {
        val rows = rowsProvider(query)
        adapter.submit(rows)
        binding.listEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    fun dismiss() {
        dialog.dismiss()
    }
}
