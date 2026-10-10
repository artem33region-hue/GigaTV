package com.artem.gigatv.data.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("movie/popular")
    suspend fun popular(@Query("page") page: Int = 1): TmdbPage

    @GET("movie/top_rated")
    suspend fun topRated(@Query("page") page: Int = 1): TmdbPage

    @GET("movie/now_playing")
    suspend fun nowPlaying(@Query("page") page: Int = 1): TmdbPage

    @GET("search/movie")
    suspend fun search(@Query("query") query: String, @Query("page") page: Int = 1): TmdbPage

    @GET("search/tv")
    suspend fun searchTv(@Query("query") query: String, @Query("page") page: Int = 1): TmdbPage

    @GET("discover/movie")
    suspend fun discover(
        @Query("with_genres") genres: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPage

    @GET("tv/popular")
    suspend fun tvPopular(@Query("page") page: Int = 1): TmdbPage

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("with_genres") genres: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPage

    @GET("movie/{id}")
    suspend fun details(@Path("id") id: Int): TmdbMovie

    @GET("tv/{id}")
    suspend fun tvDetails(@Path("id") id: Int): TmdbMovie

    @GET("movie/{id}/videos")
    suspend fun videos(@Path("id") id: Int): TmdbVideos

    @GET("tv/{id}/videos")
    suspend fun tvVideos(@Path("id") id: Int): TmdbVideos
}
