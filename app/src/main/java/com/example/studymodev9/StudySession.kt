package com.example.studymodev9

import java.util.Calendar

data class StudySession(
    val date: Long = Calendar.getInstance().timeInMillis,
    val durationInMinutes: Int,
    val dayOfWeek: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
) 