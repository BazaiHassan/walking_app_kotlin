package com.masterdev.gowalk.ui.map

import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import java.text.DecimalFormat

object MapUtil {

    fun setCameraPosition(location: LatLng): CameraPosition {
        return CameraPosition.Builder()
            .target(location)
            .zoom(18f)
            .build()
    }

    fun calculateElapseTime(startTime: Long, stopTime: Long): String {

        val elapseTime = stopTime - startTime

        val seconds = (elapseTime / 1000).toInt() % 60
        val minutes = (elapseTime / (1000 * 60) % 60)
        val hours = (elapseTime / (1000 * 60 * 60) % 24)

        return "$hours:$minutes:$seconds"
    }

    fun calculateDistance(locationList: MutableList<LatLng>): String {

        if (locationList.size > 1) {
            val meters =
                SphericalUtil.computeDistanceBetween(locationList.first(), locationList.last())
            val kilometer = meters / 1000

            return DecimalFormat("#.##").format(kilometer)
        }
        return "0.00"

    }

}