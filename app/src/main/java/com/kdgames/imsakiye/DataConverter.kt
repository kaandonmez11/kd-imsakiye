package com.kdgames.imsakiye

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class DataConverter private constructor(private val context: Context) {
    private val gson = Gson()

    companion object {
        @Volatile
        private var instance: DataConverter? = null

        fun getInstance(context: Context): DataConverter {
            return instance ?: synchronized(this) {
                instance ?: DataConverter(context.applicationContext).also { instance = it }
            }
        }
    }


}