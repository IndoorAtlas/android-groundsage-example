package com.example.groundsage_example

import com.indooratlas.android.sdk.IAGeofence

object DynamicGeofence {
    val coordDynamic =
        arrayListOf(
            doubleArrayOf(22.301250192984895,114.17400050908329),
            doubleArrayOf(22.30126104991289, 114.17405951768161),
            doubleArrayOf(22.30123313209634, 114.1740655526519),
            doubleArrayOf(22.30122165477014, 114.17400654405357),
            doubleArrayOf(22.301250192984895, 114.17400050908329)
        )
    const val dynamicGeoID = "cc08d560-3532-11eb-995b-ed87b9773c37"
    const val dynamicGeoName = "Dynamic 19.3"

    val geofenceDynamic:IAGeofence = IAGeofence.Builder().withEdges(coordDynamic).withId(
        dynamicGeoID).withName(dynamicGeoName).withFloor(19).build()

    val coordDynamicOverlap =
        arrayListOf(
            doubleArrayOf(22.301292379900392, 114.17396564036609),
            doubleArrayOf(22.301234372888302, 114.17397771030666),
            doubleArrayOf(22.301241197243915, 114.1740169376135),
            doubleArrayOf(22.301299514451, 114.17400117963554),
            doubleArrayOf(22.301292379900392, 114.17396564036609)
        )
    const val dynamicGeoOverlapID = "ef273360-3533-11eb-995b-ed87b9773c37"
    const val dynamicGeoOverlapName = "Dynamic Overlap"

    val geofenceDynamicOverlap:IAGeofence = IAGeofence.Builder().withEdges(coordDynamicOverlap).withId(
        dynamicGeoOverlapID).withName(dynamicGeoOverlapName).withFloor(19).build()

    private val coordDynamicD1 =
        arrayListOf(
            doubleArrayOf(22.30125763773561, 114.17403873056175),
            doubleArrayOf(22.301228168928365, 114.17404778301716),
            doubleArrayOf(22.301216691601756, 114.17399749159816),
            doubleArrayOf(22.301248641995112, 114.17399011552334),
            doubleArrayOf(22.30125763773561, 114.17403873056175)
        )

    private val coordDynamicD2 =
        arrayListOf(
            doubleArrayOf(22.301260119319103, 114.17405884712936),
            doubleArrayOf(22.30125298476646, 114.17401257902384),
            doubleArrayOf(22.30122258536419, 114.17402196675542),
            doubleArrayOf(22.301229409720364, 114.17406722903252),
            doubleArrayOf(22.301260119319103, 114.17405884712936)
        )
    const val dynamicGeoD1ID = "64d479b0-3534-11eb-995b-ed87b9773c37"
    private const val dynamicGeoD1Name = "Dynamic D1"

    const val dynamicGeoD2ID = "728da8b0-3534-11eb-995b-ed87b9773c37"
    private const val dynamicGeoD2Name = "Dynamic D2"

    val geofenceDynamicD1:IAGeofence = IAGeofence.Builder().withFloor(19).withEdges(coordDynamicD1).withId(
        dynamicGeoD1ID).withName(dynamicGeoD1Name).build()

    val geofenceDynamicD2:IAGeofence = IAGeofence.Builder().withFloor(19).withEdges(coordDynamicD2).withId(
        dynamicGeoD2ID).withName(dynamicGeoD2Name).build()

}
