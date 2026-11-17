package com.example.genesequenceanalyser.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.genesequenceanalyser.R
import com.example.genesequenceanalyser.db.SequenceEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var historyList: List<SequenceEntity> = emptyList(),
    private val onDeleteClick: (SequenceEntity) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    fun updateData(newList: List<SequenceEntity>) {
        historyList = newList
        notifyDataSetChanged()
    }

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAccession: TextView = itemView.findViewById(R.id.tvHistoryAccession)
        val tvDetails: TextView = itemView.findViewById(R.id.tvHistoryDetails)
        val tvDate: TextView = itemView.findViewById(R.id.tvHistoryDate)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteHistory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]
        holder.tvAccession.text = item.accessionId
        val gcFormatted = String.format(Locale.US, "%.1f", item.gcPercentage)
        holder.tvDetails.text = "Length: ${item.sequenceLength} bp | GC: $gcFormatted%"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        holder.tvDate.text = "Saved on: ${sdf.format(Date(item.timestamp))}"
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = historyList.size
}