package com.example.groundsage_example

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.indooratlas.sdk.groundsage.IAGSManager

class MainActivity : AppCompatActivity() {

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.densityListView)

        IAGSManager.getInstance(this).setApiKey(
            "5696681a-03c7-4c50-bbfd-d145a3b34ed3",
            "PTxs0YMAwTl3Mt1/25hwV5cdZGAK1QMaQahSMpBwxGbh69QwZprjHUGuelcubp0i5VeLTFqTbF+bPCCyVcBIpbZCIKKTwtTAp/1QRLTpddZDd1KHvZYy2fBnZ34kEw=="
        )

        IAGSManager.getInstance(this).requestVenueInfo { venues, error ->
            if (venues != null){
                val rows = mutableListOf<RecyclerAdapter.TableRow>()
                rows.add(RecyclerAdapter.HeaderRow("Density"))
                venues[0].densityConfig.densityLevels?.let {
                    for (i in 1..it.size){
                        rows.add(RecyclerAdapter.DensityRow(it[i-1]))
                    }
                }
                rows.add(RecyclerAdapter.HeaderRow("Floor"))
                venues[0].floors.let {
                    for(i in 1 ..it.size){
                        rows.add(RecyclerAdapter.FloorRow(it[i-1]))
                    }
                }
                rows.add(RecyclerAdapter.HeaderRow("Area"))
                venues[0].areas.let {
                    for(i in 1..it.size){
                        rows.add(RecyclerAdapter.AreaRow(it[i-1], null))
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    val adapter = RecyclerAdapter(rows)
                    recyclerView.adapter = adapter
                }
            }
            if (error != null){
                print("error %d \n" + error.message)
            }
        }
    }
}
