package com.example.groundsage_example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.indooratlas.sdk.groundsage.data.IAGSVenue
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity


class RecyclerAdapter(private val rows: List<TableRow>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface TableRow
    class HeaderRow(val title: String) : TableRow
    class DensityRow(val density: IAGSVenue.IAGSDensityLevel) : TableRow
    class FloorRow(val floor: IAGSVenue.IAGSFloor) : TableRow
    class AreaRow(val areaProperty: IAGSVenue.IAGSAreaProperty, val densityProperty: IAGSVenueDensity.IAGSDensityProperty?): TableRow

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_DENSITY = 1
        private const val TYPE_FLOOR = 2
        private const val TYPE_AREA = 3
    }

    class DensityViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val name = itemView.findViewById(R.id.densityName) as TextView
        val colorView = itemView.findViewById(R.id.densityColorView) as View
        val range = itemView.findViewById(R.id.densityRange) as TextView
    }

    class FloorViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val name = itemView.findViewById(R.id.floorName) as TextView
        val floorLevel = itemView.findViewById(R.id.floorLevel) as TextView
    }

    class AreaViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val areaName = itemView.findViewById(R.id.areaNameView) as TextView
        val floorLevel = itemView.findViewById(R.id.floorLevelView) as TextView
        val colorView = itemView.findViewById(R.id.colorView) as View
        val count = itemView.findViewById(R.id.countView) as TextView
        val densityValue = itemView.findViewById(R.id.densityValueView) as TextView
        val densityLevel = itemView.findViewById(R.id.densityLevelView) as TextView
        val densityPropertyView = itemView.findViewById(R.id.densityPropertyView) as RelativeLayout
    }

    class HeaderViewHolder(itemView: View):RecyclerView.ViewHolder(itemView){
        val headerName = itemView.findViewById(R.id.headerView) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType){
            TYPE_DENSITY -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_density, parent, false)
                return DensityViewHolder(v)
            }
            TYPE_FLOOR -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_floor, parent, false)
                return FloorViewHolder(v)
            }
            TYPE_AREA -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_area, parent, false)
                return AreaViewHolder(v)
            }
            TYPE_HEADER -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false)
                return HeaderViewHolder(v)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }

    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            TYPE_DENSITY -> onBindDensity(holder, rows[position] as DensityRow)
            TYPE_FLOOR -> onBindFloor(holder, rows[position] as FloorRow)
            TYPE_AREA ->onBindArea(holder, rows[position] as AreaRow)
            TYPE_HEADER -> onBindHeader(holder, rows[position] as HeaderRow)
            else -> throw IllegalArgumentException()
        }
    }

    private fun onBindDensity(holder: RecyclerView.ViewHolder, row: DensityRow){
        val densityRow = holder as DensityViewHolder
        densityRow.name.text = row.density.name
        densityRow.colorView.setBackgroundColor(row.density.color.toInt())
        densityRow.range.text = String.format(
            "percent range: %.2f - %.2f",
            row.density.percentRange.lowerBound,
            row.density.percentRange.upperBound)
    }

    private fun onBindFloor(holder: RecyclerView.ViewHolder, row: FloorRow){
        val floorRow = holder as FloorViewHolder
        floorRow.name.text = row.floor.name
        floorRow.floorLevel.text = String.format("floor level %d", row.floor.floorLevel)
    }

    private fun onBindArea(holder: RecyclerView.ViewHolder, row: AreaRow){
        val areaRow = holder as AreaViewHolder
        areaRow.areaName.text = row.areaProperty.description
        areaRow.floorLevel.text = String.format("floor level %d", row.areaProperty.floorLevel)
        if (row.densityProperty != null){
            areaRow.densityPropertyView.visibility = View.VISIBLE
            areaRow.colorView.setBackgroundColor(row.densityProperty.densityColor.toInt())
            areaRow.count.text = String.format("count: %d", row.densityProperty.count)
            areaRow.densityValue.text = String.format("density: %.2f", row.densityProperty.density)
            areaRow.densityLevel.text = String.format("density level: %d", row.densityProperty.densityLevel)

        }else{
            areaRow.densityPropertyView.visibility = View.GONE
        }
    }

    private fun onBindHeader(holder: RecyclerView.ViewHolder, row: HeaderRow){
        val headerRow = holder as HeaderViewHolder
        headerRow.headerName.text = row.title
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]){
            is HeaderRow -> TYPE_HEADER
            is DensityRow -> TYPE_DENSITY
            is FloorRow -> TYPE_FLOOR
            is AreaRow -> TYPE_AREA
            else -> throw IllegalArgumentException("Invalid type of data $position")
        }
    }

    override fun getItemCount(): Int {
        return rows.size
    }

}
