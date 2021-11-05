package com.masterdev.gowalk.bindingadapter

import android.view.View
import android.widget.Button
import androidx.cardview.widget.CardView
import androidx.databinding.BindingAdapter

class MapsBindingAdapter {

    companion object{
        @BindingAdapter("observeTracking")
        @JvmStatic
        fun observeTracking(view: View, started:Boolean){
            if (started && view is Button){
                view.visibility = View.VISIBLE
            }else if (started && view is CardView){
                view.visibility = View.INVISIBLE
            }
        }
    }
}