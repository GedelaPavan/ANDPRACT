package com.example.genesequenceanalyser.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NcbiApiService {
    @GET("entrez/eutils/efetch.fcgi")
    suspend fun getFastaSequence(
        @Query("db") db: String = "nuccore",
        @Query("id") accession: String,
        @Query("rettype") retType: String = "fasta",
        @Query("retmode") retMode: String = "text"
    ): Response<String>
}