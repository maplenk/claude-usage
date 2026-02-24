package com.qbapps.claudeusage.data.remote

import com.google.gson.annotations.SerializedName

data class UsageResponseDto(
    @SerializedName("five_hour")
    val fiveHour: UsageMetricDto?,

    @SerializedName("seven_day")
    val sevenDay: UsageMetricDto?,

    @SerializedName("seven_day_opus")
    val sevenDayOpus: UsageMetricDto?,

    @SerializedName("seven_day_sonnet")
    val sevenDaySonnet: UsageMetricDto?
)

data class UsageMetricDto(
    @SerializedName("utilization")
    val utilization: Double,

    @SerializedName("resets_at")
    val resetsAt: String?
)

data class OrganizationDto(
    @SerializedName("uuid")
    val uuid: String,

    @SerializedName("name")
    val name: String
)
