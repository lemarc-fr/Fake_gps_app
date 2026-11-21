package com.lemarc.fake_gps.ui

import android.content.Context
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.data.gpx.GpxParser.GpxException
import com.lemarc.fake_gps.data.routing.OsrmClient.RoutingException
import com.lemarc.fake_gps.mock.MockError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Traduit les erreurs des couches données / simulation en texte affichable (chaînes de ressources). */
object ErrorMessages {

    fun describe(context: Context, error: Throwable): String = when (error) {
        is GpxException -> context.getString(
            when (error.reason) {
                GpxException.Reason.NOT_FOUND -> R.string.error_gpx_not_found
                GpxException.Reason.UNREADABLE -> R.string.error_gpx_unreadable
                GpxException.Reason.NO_POINTS -> R.string.error_gpx_no_points
            },
        )
        is RoutingException -> when (error.reason) {
            RoutingException.Reason.SERVER_BUSY -> context.getString(R.string.error_routing_busy)
            RoutingException.Reason.INVALID_RESPONSE ->
                context.getString(R.string.error_routing_invalid, error.serverMessage.orEmpty())
            RoutingException.Reason.NO_ROUTE -> context.getString(R.string.error_routing_no_route)
            RoutingException.Reason.NO_SEGMENT -> context.getString(R.string.error_routing_no_segment)
            RoutingException.Reason.TRUNCATED_GEOMETRY -> context.getString(R.string.error_routing_truncated)
            RoutingException.Reason.SERVER_ERROR ->
                error.serverMessage ?: context.getString(R.string.error_unknown)
        }
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            context.getString(R.string.error_network)
        is IOException -> context.getString(R.string.error_io)
        else -> error.message ?: error.javaClass.simpleName
    }

    fun describe(context: Context, error: MockError): String = context.getString(
        when (error.reason) {
            MockError.Reason.NOT_ALLOWED -> R.string.error_mock_not_allowed
            MockError.Reason.NO_PROVIDER -> R.string.error_mock_no_provider
        },
    )
}
