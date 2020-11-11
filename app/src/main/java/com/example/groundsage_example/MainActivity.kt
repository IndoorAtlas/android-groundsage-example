package com.example.groundsage_example

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.groundsage_example.RecyclerAdapter.*
import com.indooratlas.sdk.groundsage.IAGSManager
import com.indooratlas.sdk.groundsage.IAGSManagerListener
import com.indooratlas.sdk.groundsage.data.IAGSVenue
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity
import kotlinx.android.synthetic.main.item_floor.view.*
import java.io.Serializable

class MainActivity : AppCompatActivity(), IAGSManagerListener, ClickEventHandler {

    private var rows = mutableListOf<TableRow>()
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: RecyclerAdapter

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById<RecyclerView>(R.id.densityListView)

        IAGSManager.getInstance(this).setApiKey(
            "5696681a-03c7-4c50-bbfd-d145a3b34ed3",
            "PTxs0YMAwTl3Mt1/25hwV5cdZGAK1QMaQahSMpBwxGbh69QwZprjHUGuelcubp0i5VeLTFqTbF+bPCCyVcBIpbZCIKKTwtTAp/1QRLTpddZDd1KHvZYy2fBnZ34kEw=="
        )

        IAGSManager.getInstance(this).requestVenueInfo { venues, error ->
            if (venues != null) {
                Venue.setVenue(venues[0])
                val areaThreshold = String.format(
                    "Closed Area Threshold: %d",
                    venues[0].densityConfig.closedAreaThreshold
                )
                rows.add(HeaderRow(areaThreshold))
                rows.add(HeaderRow("Density"))
                venues[0].densityConfig.densityLevels?.let {
                    for (i in 1..it.size) {
                        rows.add(DensityRow(it[i - 1]))
                    }
                }
                rows.add(HeaderRow("Floor"))
                venues[0].floors.let {
                    for (i in 1..it.size) {
                        rows.add(FloorRow(it[i - 1]))
                    }
                }
                rows.add(HeaderRow("Area"))
                venues[0].areas.let {
                    for (i in 1..it.size) {
                        rows.add(AreaRow(it[i - 1], null))
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    adapter = RecyclerAdapter(rows, this)
                    recyclerView.adapter = adapter
                    IAGSManager.getInstance(this).addGroundSageListener(this)
                    IAGSManager.getInstance(this).startSubscription(venues[0].id)
                }
            }
            if (error != null) {
                print("error %d \n" + error.message)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume")
        IAGSManager.getInstance(this).addGroundSageListener(this)
        Venue.getVenue()?.id?.let { IAGSManager.getInstance(this).startSubscription(it) }
    }

    override fun didReceiveDensity(venueDensity: IAGSVenueDensity) {
        Log.d("MainActivity", "didReceiveDensity")
        //update density status
        venueDensity.area?.let {
            for (i in 1 .. it.size){
                var item = rows.first { row ->
                    row is AreaRow && row.areaProperty.id == it[i-1].id
                }
                var area = item as AreaRow
                area.densityProperty = it[i-1].densityProperty
            }
            Venue.setAreaList(rows)
            Handler(Looper.getMainLooper()).post {
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun forwardClick(holder: FloorViewHolder) {
        Log.d("MainActivity", holder.floorLevel.text as String)
        val intent = Intent(this, GMapActivity::class.java)
        intent.putExtra("floor", holder.floor)
        startActivity(intent)
    }
}
