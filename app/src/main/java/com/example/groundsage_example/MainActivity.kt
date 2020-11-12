package com.example.groundsage_example

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.groundsage_example.RecyclerAdapter.*
import com.indooratlas.sdk.groundsage.IAGSManager
import com.indooratlas.sdk.groundsage.IAGSManagerListener
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity

class MainActivity : AppCompatActivity(), IAGSManagerListener, ClickEventHandler {

    private var rows = mutableListOf<TableRow>()
    lateinit var recyclerView: RecyclerView

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var subscriptionSwitch: Switch
    lateinit var adapter: RecyclerAdapter

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        recyclerView = findViewById<RecyclerView>(R.id.densityListView)
        subscriptionSwitch = findViewById<Switch>(R.id.subscriptionSwitch)
        subscriptionSwitch.setOnClickListener {
            if (subscriptionSwitch.isChecked) {
                Venue.getVenue()?.let {
                    IAGSManager.getInstance(this).startSubscription(it.id)
                }
            } else {
                Venue.getVenue()?.let {
                    IAGSManager.getInstance(this).stopSubscription(it.id)
                }
            }
        }
        IAGSManager.getInstance(this).requestVenueInfo { venues, error ->
            if (venues != null) {
                subscriptionSwitch.isEnabled = true
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
                adapter = RecyclerAdapter(rows, this)
                recyclerView.adapter = adapter
                IAGSManager.getInstance(this).addGroundSageListener(this)
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
            for (i in 1..it.size) {
                var item = rows.first { row ->
                    row is AreaRow && row.areaProperty.id == it[i - 1].id
                }
                var area = item as AreaRow
                area.densityProperty = it[i - 1].densityProperty
            }
            Venue.setAreaList(rows)
            adapter.notifyDataSetChanged()
        }
    }

    override fun forwardClick(holder: FloorViewHolder) {
        Log.d("MainActivity", holder.floorLevel.text as String)
        val intent = Intent(this, GMapActivity::class.java)
        intent.putExtra("floor", holder.floor)
        startActivity(intent)
    }
}
