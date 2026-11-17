package com.example.genesequenceanalyser.network

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://eutils.ncbi.nlm.nih.gov/"

    val apiService: NcbiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(NcbiApiService::class.java)
    }
}