/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 17/3/2022.
 */

package com.adyen.checkout.sessions.api

import com.adyen.checkout.core.api.Connection
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.core.model.toStringPretty
import com.adyen.checkout.sessions.model.setup.SessionSetupRequest
import com.adyen.checkout.sessions.model.setup.SessionSetupResponse
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject

private val TAG = LogUtil.getTag()
private const val ENDPOINT = "v1/sessions/"

class SessionSetupConnection(
    private val request: SessionSetupRequest,
    environment: Environment,
    sessionId: String,
    clientKey: String
) : Connection<SessionSetupResponse>(
    "${environment.baseUrl}$ENDPOINT$sessionId/setup?clientKey=$clientKey"
) {
    @Throws(IOException::class, JSONException::class)
    override fun call(): SessionSetupResponse {
        Logger.v(TAG, "call - $url")
        val requestJson = SessionSetupRequest.SERIALIZER.serialize(request)
        Logger.v(TAG, "request - ${requestJson.toStringPretty()}")
        val result = post(CONTENT_TYPE_JSON_HEADER, requestJson.toString().toByteArray(Charsets.UTF_8))
        val resultJson = JSONObject(String(result, Charsets.UTF_8))
        Logger.v(TAG, "response: ${resultJson.toStringPretty()}")
        return SessionSetupResponse.SERIALIZER.deserialize(resultJson)
    }
}