package com.aegis.browser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aegis.browser.R
import com.aegis.browser.core.Urls
import com.aegis.browser.databinding.ItemTabBinding

class TabsAdapter(
    private val onSelect: (BrowserTab) -> Unit,
    private val onClose: (BrowserTab) -> Unit
) : ListAdapter<BrowserTab, TabsAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemTabBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemTabBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tab: BrowserTab) {
            binding.tabTitle.text = tab.label
            binding.tabLetter.text = Urls.letterFor(tab.title, tab.url)
            binding.tabUrl.text = if (tab.isHome) {
                binding.root.context.getString(R.string.tab_title_new)
            } else {
                Urls.pretty(tab.url)
            }
            binding.tabIncognito.visibility = if (tab.incognito) View.VISIBLE else View.GONE
            binding.tabClose.setOnClickListener { onClose(tab) }
            binding.root.setOnClickListener { onSelect(tab) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BrowserTab>() {
            override fun areItemsTheSame(oldItem: BrowserTab, newItem: BrowserTab): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: BrowserTab, newItem: BrowserTab): Boolean =
                oldItem.url == newItem.url &&
                    oldItem.title == newItem.title &&
                    oldItem.incognito == newItem.incognito
        }
    }
}
