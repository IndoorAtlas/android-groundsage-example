package com.example.groundsage_example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class NetworkViewModel : ViewModel() {
    val networkAvailable = MutableLiveData<Boolean>()
    val networkLiveData:LiveData<Boolean>
        get() = networkAvailable

    val networkChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context?.let {
                val connectivityManager = it.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkInfo = connectivityManager.activeNetworkInfo
                networkAvailable.postValue(networkInfo != null && networkInfo.isConnected)
            }
        }
    }
}
