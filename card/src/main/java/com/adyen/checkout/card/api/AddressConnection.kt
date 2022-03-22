/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by ozgur on 11/3/2022.
 */

package com.adyen.checkout.card.api

import com.adyen.checkout.card.api.model.AddressItem
import com.adyen.checkout.core.api.Connection
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import org.json.JSONArray

private val TAG = LogUtil.getTag()
private const val ENDPOINT = "datasets/"
private const val JSON_SUFFIX = ".json"

class AddressConnection(
    environment: Environment,
    dataType: AddressDataType,
    localeString: String,
    countryCode: String?
) : Connection<List<AddressItem>>(makeUrl(environment, dataType, localeString, countryCode)) {

    override fun call(): List<AddressItem> {
        Logger.v(TAG, "call - $url")
        val result = get(CONTENT_TYPE_JSON_HEADER)
        val resultJson = JSONArray(String(result, Charsets.UTF_8))
        resultJson.toString()
        Logger.v(TAG, "response: ${resultJson.toString(4)}")
        return parseOptAddressItemList(resultJson).orEmpty()
    }

    private fun parseOptAddressItemList(jsonArray: JSONArray?): List<AddressItem>? {
        if (jsonArray == null) {
            return null
        }
        val list = mutableListOf<AddressItem>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i)
            if (item != null) {
                list.add(AddressItem.SERIALIZER.deserialize(item))
            }
        }
        return list
    }
}

fun makeUrl(
    environment: Environment,
    dataType: AddressDataType,
    localeString: String,
    countryCode: String? = null
) : String {
    return when (dataType) {
        AddressDataType.COUNRTY -> "${environment.baseUrl}$ENDPOINT${dataType.pathParam}/$localeString$JSON_SUFFIX"
        AddressDataType.STATE -> "${environment.baseUrl}$ENDPOINT${dataType.pathParam}/$countryCode/$localeString$JSON_SUFFIX"
    }
}

enum class AddressDataType(val pathParam: String) {
    COUNRTY("countries"),
    STATE("states")
}
