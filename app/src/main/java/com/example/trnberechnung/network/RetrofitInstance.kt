package com.example.trnberechnung.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
object RetrofitInstance {

    private const val BSH_BASE_URL = "https://gdi.bsh.de/"
    private const val DWD_BASE_URL = "https://api.brightsky.dev/"

    val bshApi: BshApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BSH_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BshApiService::class.java)
    }

    val dwdApi: DwdApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DWD_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DwdApiService::class.java)
    }
}