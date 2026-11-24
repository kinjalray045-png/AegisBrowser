package com.aegis.browser.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aegis.browser.databinding.ItemSuggestionBinding

data class Suggestion(
    val title: String,
    val subtitle: String?,
    val type: String,
    val iconRes: Int,
    val url: String
)

class SuggestionAdapter(
    private val onClick: (Suggestion) -> Unit
) : ListAdapter<Suggestion, SuggestionAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSuggestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Suggestion) {
            binding.suggestionIcon.setImageResource(item.iconRes)
            binding.suggestionTitle.text = item.title
            if (item.subtitle.isNullOrBlank()) {
                binding.suggestionSubtitle.visibility = ViewGroup.GONE
            } else {
                binding.suggestionSubtitle.visibility = ViewGroup.VISIBLE
                binding.suggestionSubtitle.text = item.subtitle
            }
            binding.suggestionType.text = item.type
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Suggestion>() {
            override fun areItemsTheSame(oldItem: Suggestion, newItem: Suggestion): Boolean =
                oldItem.url == newItem.url

            override fun areContentsTheSame(oldItem: Suggestion, newItem: Suggestion): Boolean =
                oldItem == newItem
        }
    }
}
