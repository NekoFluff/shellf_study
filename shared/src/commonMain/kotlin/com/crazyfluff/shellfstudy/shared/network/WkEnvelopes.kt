package com.crazyfluff.shellfstudy.shared.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wraps a single top-level resource returned by an endpoint like GET /user. */
@Serializable
data class WkSingleResponse<T>(
    @SerialName("object") val objectType: String,
    val url: String,
    @SerialName("data_updated_at") val dataUpdatedAt: String? = null,
    val data: T
)

/** Wraps one item inside a WaniKani collection (e.g. one assignment inside GET /assignments). */
@Serializable
data class WkResourceItem<T>(
    val id: Long,
    @SerialName("object") val objectType: String,
    val url: String,
    @SerialName("data_updated_at") val dataUpdatedAt: String? = null,
    val data: T
)

/** Wraps a paginated collection returned by endpoints like GET /assignments or GET /subjects. */
@Serializable
data class WkCollectionResponse<T>(
    @SerialName("object") val objectType: String,
    val url: String,
    @SerialName("data_updated_at") val dataUpdatedAt: String? = null,
    val pages: WkPages? = null,
    @SerialName("total_count") val totalCount: Int = 0,
    val data: List<WkResourceItem<T>> = emptyList()
)

@Serializable
data class WkPages(
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("next_url") val nextUrl: String? = null,
    @SerialName("previous_url") val previousUrl: String? = null
)
