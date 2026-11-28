package com.furugit.app.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtTime(ms: Long): String = SimpleDateFormat("d MMM yyyy HH:mm", Locale("id")).format(Date(ms))
