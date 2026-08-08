package com.mfp.filemanager.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.SimpleFormatter

object DateFormatter{
    private val formatter = SimpleDateFormat("dd MMM yy hh:mm a",Locale.getDefault())

    fun formatDate(date : Date) = formatter.format(date)
}
fun Date.toReadableDate() = DateFormatter.formatDate(this)

