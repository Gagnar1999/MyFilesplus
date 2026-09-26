package com.mfp.filemanager.utils

import android.app.Activity
import android.content.res.Configuration
import com.mfp.filemanager.R

class ThemeHelper(val activity: Activity){

    fun setTheme(themeMode : Int){
        when (themeMode) {
            1 -> activity.setTheme(R.style.Theme_FileManager) // Light
            2 -> activity.setTheme(R.style.Theme_FileManager_Dark) // Dark (Grey)
            3 -> activity.setTheme(R.style.Theme_FileManager_Amoled) // Amoled (Black)
            else -> {
                // System Default
                val isNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (isNight) {
                    activity.setTheme(R.style.Theme_FileManager_Dark)
                } else {
                    activity.setTheme(R.style.Theme_FileManager)
                }
            }
        }
    }

}
