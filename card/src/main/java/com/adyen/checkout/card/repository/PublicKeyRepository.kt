/*
 * Copyright (c) 2021 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 22/2/2021.
 */

package com.adyen.checkout.card.repository

import com.adyen.checkout.card.CardValidationUtils
import com.adyen.checkout.card.api.PublicKeyConnection
import com.adyen.checkout.components.api.suspendedCall
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import org.json.JSONException
import java.io.IOException

private val TAG = LogUtil.getTag()
private const val CONNECTION_RETRIES = 3

class PublicKeyRepository {
    suspend fun fetchPublicKey(environment: Environment, clientKey: String, configurationPublicKey: String): String {
        return if (configurationPublicKey.isNotEmpty() && CardValidationUtils.isPublicKeyValid(configurationPublicKey)) {
            Logger.d(TAG, "returning configuration publicKey")
            configurationPublicKey
        } else {
            Logger.d(TAG, "fetching publicKey from API")
            repeat(CONNECTION_RETRIES) {
                try {
                    PublicKeyConnection(environment, clientKey).suspendedCall()
                } catch (e: IOException) {
                    Logger.e(TAG, "PublicKeyConnection Failed", e)
                } catch (e: JSONException) {
                    Logger.e(TAG, "PublicKeyConnection unexpected result", e)
                }
            }
            ""
        }
    }
}
