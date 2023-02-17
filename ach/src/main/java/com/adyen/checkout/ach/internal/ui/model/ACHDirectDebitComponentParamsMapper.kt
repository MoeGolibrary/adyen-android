/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by onurk on 16/2/2023.
 */

package com.adyen.checkout.ach.internal.ui.model

import com.adyen.checkout.ach.ACHDirectDebitAddressConfiguration
import com.adyen.checkout.ach.ACHDirectDebitConfiguration
import com.adyen.checkout.components.base.ComponentParams
import com.adyen.checkout.components.ui.AddressParams

internal class ACHDirectDebitComponentParamsMapper {

    fun mapToParams(
        configuration: ACHDirectDebitConfiguration,
        overrideComponentParams: ComponentParams? = null
    ): ACHDirectDebitComponentParams {
        return configuration.mapToParamsInternal().override(overrideComponentParams)
    }

    private fun ACHDirectDebitConfiguration.mapToParamsInternal(): ACHDirectDebitComponentParams {
        return ACHDirectDebitComponentParams(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey,
            isAnalyticsEnabled = isAnalyticsEnabled ?: true,
            isCreatedByDropIn = false,
            amount = amount,
            isSubmitButtonVisible = isSubmitButtonVisible ?: true,
            addressParams = addressConfiguration?.mapToAddressParam()
                ?: AddressParams.FullAddress(
                    supportedCountryCodes = DEFAULT_SUPPORTED_COUNTRY_LIST,
                    addressFieldPolicy = AddressFieldPolicyParams.Required
                )
        )
    }

    private fun ACHDirectDebitAddressConfiguration.mapToAddressParam(): AddressParams {
        return when (this) {
            is ACHDirectDebitAddressConfiguration.None -> {
                AddressParams.None
            }
            is ACHDirectDebitAddressConfiguration.FullAddress -> {
                AddressParams.FullAddress(
                    supportedCountryCodes = supportedCountryCodes,
                    addressFieldPolicy = AddressFieldPolicyParams.Required
                )
            }
        }
    }

    private fun ACHDirectDebitComponentParams.override(
        overrideComponentParams: ComponentParams?
    ): ACHDirectDebitComponentParams {
        if (overrideComponentParams == null) return this
        return copy(
            shopperLocale = overrideComponentParams.shopperLocale,
            environment = overrideComponentParams.environment,
            clientKey = overrideComponentParams.clientKey,
            isAnalyticsEnabled = overrideComponentParams.isAnalyticsEnabled,
            isCreatedByDropIn = overrideComponentParams.isCreatedByDropIn,
            amount = overrideComponentParams.amount,
        )
    }

    companion object {
        private val DEFAULT_SUPPORTED_COUNTRY_LIST = listOf("US", "PR")
    }
}