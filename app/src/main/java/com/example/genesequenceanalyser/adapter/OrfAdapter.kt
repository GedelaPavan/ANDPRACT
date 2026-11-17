package com.example.genesequenceanalyser.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import biokt.Orf
import com.example.genesequenceanalyser.R

class OrfAdapter(
    private var orfList: List<Orf> = emptyList()
) : RecyclerView.Adapter<OrfAdapter.OrfViewHolder>() {

    fun updateData(newList: List<Orf>) {
        orfList = newList
        notifyDataSetChanged()
    }

    class OrfViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvHeader: TextView = itemView.findViewById(R.id.tvOrfHeader)
        val tvStrand: TextView = itemView.findViewById(R.id.tvOrfStrand)
        val tvLength: TextView = itemView.findViewById(R.id.tvOrfLength)
        val tvProtein: TextView = itemView.findViewById(R.id.tvOrfProtein)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrfViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_orf, parent, false)
        return OrfViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrfViewHolder, position: Int) {
        val item = orfList[position]
        holder.tvHeader.text = "ORF [Pos: ${item.start}..${item.end}]"
        holder.tvStrand.text = "Strand: ${item.strand}${item.frame}"
        holder.tvLength.text = "Length: ${item.length} bp"
        val preview = if (item.protein.sequence.length > 30) {
            item.protein.sequence.take(30) + "..."
        } else {
            item.protein.sequence
        }
        holder.tvProtein.text = "Protein: $preview"
    }

    override fun getItemCount(): Int = orfList.size
}