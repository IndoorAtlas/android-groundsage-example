package com.example.groundsage_example

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.ConnectivityManager
import androidx.lifecycle.*
import com.indooratlas.android.sdk.IARegion
import java.text.SimpleDateFormat
import java.util.*

class NetworkViewModel : ViewModel() {
    val networkAvailable = MutableLiveData<Boolean>()
    val bluetoothAvailable = MutableLiveData<Boolean>()
    val locationPermissionGranted = MutableLiveData<Boolean>()
    val locationServiceAvailable = MutableLiveData<Boolean>()
    val exitRegionMsg = MutableLiveData<Boolean>()
    val traceID = MutableLiveData<String>().apply { postValue("Trace ID: NA") }
    val lastUpdateDate = MutableLiveData<String>().apply { postValue("Last density update: NA") }
    val selectedFloorLevel = MutableLiveData<Int>()
    val showMapView : LiveData<Boolean> = Transformations.map(selectedFloorLevel) {level ->
        level != -999
    }

    val region = MutableLiveData<IARegion>()

    val activityTitle: LiveData<String> = MediatorLiveData<String>().apply {
        val produce: () -> String? = {
            showMapView.value?.let {
                if (it) {
                    String.format("Floor ${selectedFloorLevel.value}")
                } else {
                    "Venue information"
                }
            }
        }
        addSource(showMapView) {
            value = produce()
        }
        addSource(selectedFloorLevel) {
            value = produce()
        }
    }

    fun hideMapView() {
        selectedFloorLevel.postValue(-999)
    }

    fun updateLastDensityUpdate() {
        val currentDateTime =
            SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date())
        lastUpdateDate.postValue(String.format("Last density update: $currentDateTime"))
    }

    val networkChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when {
                intent?.action.equals(ConnectivityManager.CONNECTIVITY_ACTION) -> {
                    context?.let {
                        updateNetworkStatus(it)
                    }
                }
                intent?.action.equals(BluetoothAdapter.ACTION_STATE_CHANGED) -> {
                    bluetoothAvailable.postValue(
                        intent?.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE,
                            -1
                        ) == BluetoothAdapter.STATE_ON
                    )
                }
                intent?.action.equals(LocationManager.PROVIDERS_CHANGED_ACTION) -> {
                    context?.let {
                        updateLocationServiceStatus(it)
                    }
                }
            }
        }
    }

    fun updateNetworkStatus(context: Context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        networkAvailable.postValue(networkInfo != null && networkInfo.isConnected)
    }

    fun updateBluetoothStatus() {
        bluetoothAvailable.postValue(BluetoothAdapter.getDefaultAdapter().isEnabled)
    }

    fun updateLocationServiceStatus(context: Context) {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled =
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        locationServiceAvailable.postValue(isGpsEnabled or isNetworkEnabled)
    }

    fun initWarningMessageStatus(context: Context) {
        updateNetworkStatus(context)
        updateBluetoothStatus()
        updateLocationServiceStatus(context)
    }

}
