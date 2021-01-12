package com.example.groundsage_example

import com.indooratlas.sdk.groundsage.data.IAGSVenue

object Venue {

    private var venue : IAGSVenue? = null

    val areaList = mutableListOf<RecyclerAdapter.AreaRow>()

    fun getFilteredAreaList(floor: Int):List<RecyclerAdapter.AreaRow>{
        return areaList.filter {
            it.areaProperty.floorLevel == floor
        }
    }

    fun setAreaList(list: List<RecyclerAdapter.TableRow>){
        val list = list.filterIsInstance<RecyclerAdapter.AreaRow>()
        this.areaList.addAll(list)
    }

    fun setVenue(v:IAGSVenue){
        this.venue = v
    }

    fun getVenue():IAGSVenue?{
        return this.venue
    }

}
