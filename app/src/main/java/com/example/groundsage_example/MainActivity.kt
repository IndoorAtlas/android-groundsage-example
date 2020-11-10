package com.example.groundsage_example

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.indooratlas.sdk.groundsage.IAGSManager
import com.indooratlas.sdk.groundsage.IAGSManagerListener
import com.indooratlas.sdk.groundsage.data.IAGSVenue
import com.indooratlas.sdk.groundsage.data.IAGSVenueDensity

class MainActivity : AppCompatActivity(), IAGSManagerListener {

    private var rows = mutableListOf<RecyclerAdapter.TableRow>()
    lateinit var venue:IAGSVenue
    lateinit var recyclerView: RecyclerView

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
                venue = venues[0]
                val areaThreshold = String.format(
                    "Closed Area Threshold: %d",
                    venues[0].densityConfig.closedAreaThreshold
                )
                rows.add(RecyclerAdapter.HeaderRow(areaThreshold))
                rows.add(RecyclerAdapter.HeaderRow("Density"))
                venues[0].densityConfig.densityLevels?.let {
                    for (i in 1..it.size) {
                        rows.add(RecyclerAdapter.DensityRow(it[i - 1]))
                    }
                }
                rows.add(RecyclerAdapter.HeaderRow("Floor"))
                venues[0].floors.let {
                    for (i in 1..it.size) {
                        rows.add(RecyclerAdapter.FloorRow(it[i - 1]))
                    }
                }
                rows.add(RecyclerAdapter.HeaderRow("Area"))
                venues[0].areas.let {
                    for (i in 1..it.size) {
                        rows.add(RecyclerAdapter.AreaRow(it[i - 1], null))
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    val adapter = RecyclerAdapter(rows)
                    recyclerView.adapter = adapter
                }

                IAGSManager.getInstance(this).addGroundSageListener(this)
                IAGSManager.getInstance(this).startSubscription(venues[0].id)
            }
            if (error != null) {
                print("error %d \n" + error.message)
            }
        }
    }

    override fun didReceiveDensity(venueDensity: IAGSVenueDensity) {
        //update density status
        venueDensity.area?.let {
            for (i in 1 .. it.size){
                var item = rows.first { row ->
                    row is RecyclerAdapter.AreaRow && row.areaProperty.id == it[i-1].id
                }
                var area = item as RecyclerAdapter.AreaRow
                area.densityProperty = it[i-1].densityProperty
            }
            recyclerView.adapter?.notifyDataSetChanged()
        }
    }
}
