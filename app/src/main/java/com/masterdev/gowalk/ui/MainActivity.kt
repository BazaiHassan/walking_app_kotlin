package com.masterdev.gowalk.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.masterdev.gowalk.R
import com.masterdev.gowalk.utils.Permissions.hasLocationPermissions

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navController = findNavController(R.id.navHostFragment)

        if (hasLocationPermissions(this)) {
            navController.navigate(R.id.action_permissionFragment_to_mapsFragment)
        }
    }
}