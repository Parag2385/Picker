package com.appexecutors.picker.utils

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.view.View.*
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.appexecutors.picker.R
import java.text.SimpleDateFormat
import java.util.*

object GeneralUtils {

    fun manipulateBottomSheetVisibility(activity: Activity, slideOffSet: Float, recyclerViewInstantMedia: RecyclerView, recyclerViewBottomSheetMedia: RecyclerView,
                                        constraintBottomSheetTop: ConstraintLayout){

        recyclerViewInstantMedia.alpha = 1 - slideOffSet
        constraintBottomSheetTop.alpha =     slideOffSet
        recyclerViewBottomSheetMedia.alpha = slideOffSet

        if ((1 - slideOffSet) == 0f && recyclerViewInstantMedia.visibility == VISIBLE){
            recyclerViewInstantMedia.visibility = GONE
        }else if(recyclerViewInstantMedia.visibility == GONE && (1 - slideOffSet) > 0f){
            recyclerViewInstantMedia.visibility = VISIBLE
        }

        if (slideOffSet > 0f && recyclerViewBottomSheetMedia.visibility == INVISIBLE){
            recyclerViewBottomSheetMedia.visibility = VISIBLE
            constraintBottomSheetTop.visibility = VISIBLE
            showStatusBar(activity)
        }else if (slideOffSet == 0f && recyclerViewBottomSheetMedia.visibility == VISIBLE){
            recyclerViewBottomSheetMedia.visibility = INVISIBLE
            constraintBottomSheetTop.visibility = GONE
            hideStatusBar(activity)
        }
    }

    fun hideStatusBar(appCompatActivity: Activity) {
        synchronized(appCompatActivity) {
            appCompatActivity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    fun showStatusBar(appCompatActivity: Activity) {
        synchronized(appCompatActivity) {
            appCompatActivity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    fun getStringDate(context: Context, time: Long): String{
        //
        val date = Date(time * 1000)
        val calendar = Calendar.getInstance()
        calendar.time = date

        val lastMonth = Calendar.getInstance()
        val lastWeek = Calendar.getInstance()
        val recent = Calendar.getInstance()
        lastMonth.add(Calendar.DAY_OF_MONTH, -Calendar.DAY_OF_MONTH)
        lastWeek.add(Calendar.DAY_OF_MONTH, -7)
        recent.add(Calendar.DAY_OF_MONTH, -2)
        return if (calendar.before(lastMonth)) {
            SimpleDateFormat("MMMM", Locale.ENGLISH).format(date)
        } else if (calendar.after(lastMonth) && calendar.before(lastWeek)) {
            context.resources.getString(R.string.last_month)
        } else if (calendar.after(lastWeek) && calendar.before(recent)) {
            context.resources.getString(R.string.last_week)
        } else {
            context.resources.getString(R.string.recent)
        }
    }

    fun getScreenWidth(activity: Activity): Int {
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics.widthPixels
    }

    fun convertDpToPixel(dp: Float, context: Context): Float {
        val resources = context.resources
        val metrics = resources.displayMetrics
        return dp * (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }

    fun convertPixelsToDp(px: Float, context: Context): Float {
        val resources = context.resources
        val metrics = resources.displayMetrics
        return px / (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }
}