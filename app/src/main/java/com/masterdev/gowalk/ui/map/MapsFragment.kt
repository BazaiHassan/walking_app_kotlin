package com.masterdev.gowalk.ui.map

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import androidx.fragment.app.Fragment

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.masterdev.gowalk.R
import com.masterdev.gowalk.databinding.FragmentMapsBinding
import com.masterdev.gowalk.model.Result
import com.masterdev.gowalk.service.TrackerService
import com.masterdev.gowalk.ui.map.MapUtil.calculateDistance
import com.masterdev.gowalk.ui.map.MapUtil.calculateElapseTime
import com.masterdev.gowalk.ui.map.MapUtil.setCameraPosition
import com.masterdev.gowalk.utils.Constants.ACTION_SERVICE_START
import com.masterdev.gowalk.utils.Constants.ACTION_SERVICE_STOP
import com.masterdev.gowalk.utils.ExtensionFunctions.disable
import com.masterdev.gowalk.utils.ExtensionFunctions.enable
import com.masterdev.gowalk.utils.ExtensionFunctions.hide
import com.masterdev.gowalk.utils.ExtensionFunctions.show
import com.masterdev.gowalk.utils.Permissions.hasBackgroundLocationPermission
import com.masterdev.gowalk.utils.Permissions.requestBackgroundLocationPermission
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.dialogs.SettingsDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MapsFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener,
    EasyPermissions.PermissionCallbacks, GoogleMap.OnMarkerClickListener {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private lateinit var map: GoogleMap
    val started = MutableLiveData(false)
    private var locationList = mutableListOf<LatLng>()
    private var polyLineList = mutableListOf<Polyline>()
    private var markerList = mutableListOf<Marker>()
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private var startTime = 0L
    private var stopTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        binding.tracking = this
        binding.startButton.setOnClickListener {
            onStartButtonClicked()
        }
        binding.stopButton.setOnClickListener {
            onStopButtonClicked()
        }
        binding.resetButton.setOnClickListener {
            onResetButtonClicked()
        }

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireActivity())

        return binding.root
    }

    private fun onResetButtonClicked() {
        resetMap()
    }

    @SuppressLint("MissingPermission")
    private fun resetMap() {
        fusedLocationProviderClient.lastLocation.addOnCompleteListener {
            val lastKnownLocation = LatLng(
                it.result.latitude,
                it.result.longitude
            )
            for (polyLine in polyLineList) {
                polyLine.remove()
            }
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    setCameraPosition(lastKnownLocation)
                )
            )

            for (marker in markerList) {
                marker.remove()
            }
            locationList.clear()
            markerList.clear()
            binding.resetButton.hide()
            binding.startButton.show()
        }
    }


    private fun onStartButtonClicked() {
        if (hasBackgroundLocationPermission(requireContext())) {
            startCountDown()
            binding.startButton.disable()
            binding.startButton.hide()
            binding.stopButton.show()
        } else {
            requestBackgroundLocationPermission(this)
        }
    }

    private fun onStopButtonClicked() {
        stopForegroundService()
        binding.stopButton.hide()
        binding.startButton.show()
    }


    private fun startCountDown() {
        binding.timerTextView.show()
        binding.stopButton.disable()
        val timer: CountDownTimer = object : CountDownTimer(4000, 1000) {
            override fun onTick(milliSecondUntilFinished: Long) {
                val currentSecond = milliSecondUntilFinished / 1000
                if (currentSecond.toString() == "0") {
                    binding.timerTextView.text = "Go"
                } else {
                    binding.timerTextView.text = currentSecond.toString()
                }
            }

            override fun onFinish() {
                sendActionCommandToService(ACTION_SERVICE_START)
                binding.timerTextView.hide()
                binding.stopButton.enable()
            }

        }

        timer.start()
    }

    private fun stopForegroundService() {
        binding.startButton.disable()
        sendActionCommandToService(ACTION_SERVICE_STOP)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    @SuppressLint("MissingPermission", "PotentialBehaviorOverride")
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap!!
        map.setOnMyLocationButtonClickListener(this)
        map.setOnMarkerClickListener(this)
        map.isMyLocationEnabled = true
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isZoomGesturesEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            isScrollGesturesEnabled = false
        }
        observeTrackerService()
    }

    private fun observeTrackerService() {
        TrackerService.locationList.observe(viewLifecycleOwner, {
            if (it != null) {
                locationList = it
                if (locationList.size > 1) {
                    binding.stopButton.enable()
                }
                drawPolyLine()
                followPolyLine()
            }
        })

        TrackerService.started.observe(viewLifecycleOwner, {
            started.value = it
        })

        TrackerService.startTime.observe(viewLifecycleOwner, {
            startTime = it
        })

        TrackerService.stopTime.observe(viewLifecycleOwner, {
            stopTime = it
            if (stopTime != 0L) {
                showBiggerPicture()
                displayResult()
            }
        })
    }

    private fun showBiggerPicture() {
        val bounds = LatLngBounds.builder()
        for (location in locationList) {
            bounds.include(location)
        }

        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(), 100
            ), 2000, null
        )
        addMarker(locationList.first())
        addMarker(locationList.last())
    }

    private fun addMarker(position: LatLng) {
        val marker = map.addMarker(MarkerOptions().position(position))
        if (marker != null) {
            markerList.add(marker)
        }
    }

    private fun displayResult() {
        val result = Result(
            calculateDistance(locationList),
            calculateElapseTime(startTime, stopTime)
        )
        lifecycleScope.launch {
            delay(2500)
            val direction = MapsFragmentDirections.actionMapsFragmentToResultFragment(result)
            findNavController().navigate(direction)
            binding.startButton.apply {
                hide()
                enable()
            }
            binding.stopButton.hide()
            binding.resetButton.show()
        }
    }

    private fun drawPolyLine() {
        val polyline = map.addPolyline(
            PolylineOptions().apply {
                width(10f)
                color(Color.CYAN)
                jointType(JointType.ROUND)
                startCap(ButtCap())
                endCap(ButtCap())
                addAll(locationList)
            }
        )

        polyLineList.add(polyline)
    }

    private fun followPolyLine() {
        if (locationList.isNotEmpty()) {
            map.animateCamera(
                (
                        CameraUpdateFactory.newCameraPosition(setCameraPosition(locationList.last()))
                        ), 1000, null
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onMyLocationButtonClick(): Boolean {
        binding.hintTextView.animate().alpha(0f).duration = 1500
        lifecycleScope.launch {
            delay(2500)
            binding.hintTextView.hide()
            binding.startButton.show()
        }
        return false
    }


    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        onStartButtonClicked()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        if (EasyPermissions.permissionPermanentlyDenied(this, perms[0])) {
            SettingsDialog.Builder(requireActivity()).build().show()
        } else {
            requestBackgroundLocationPermission(this)
        }
    }

    private fun sendActionCommandToService(action: String) {
        Intent(requireContext(), TrackerService::class.java).apply {
            this.action = action
            requireContext().startService(this)
        }
    }

    override fun onMarkerClick(p0: Marker): Boolean {
        return true
    }
}