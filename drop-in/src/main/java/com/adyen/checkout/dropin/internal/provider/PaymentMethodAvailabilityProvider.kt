/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by oscars on 8/12/2023.
 */

package com.adyen.checkout.dropin.internal.provider

import android.app.Application
import com.adyen.checkout.components.core.Amount
import com.adyen.checkout.components.core.ComponentAvailableCallback
import com.adyen.checkout.components.core.PaymentMethod
import com.adyen.checkout.components.core.PaymentMethodTypes
import com.adyen.checkout.components.core.internal.AlwaysAvailablePaymentMethod
import com.adyen.checkout.components.core.internal.Configuration
import com.adyen.checkout.components.core.internal.NotAvailablePaymentMethod
import com.adyen.checkout.components.core.internal.PaymentMethodAvailabilityCheck
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.internal.util.LogUtil
import com.adyen.checkout.core.internal.util.Logger
import com.adyen.checkout.core.internal.util.runCompileOnly
import com.adyen.checkout.dropin.DropInConfiguration
import com.adyen.checkout.googlepay.internal.provider.GooglePayComponentProvider
import com.adyen.checkout.sessions.core.internal.data.model.SessionDetails
import com.adyen.checkout.sessions.core.internal.data.model.mapToParams
import com.adyen.checkout.wechatpay.WeChatPayProvider

private val TAG = LogUtil.getTag()

@Suppress("LongParameterList")
internal fun checkPaymentMethodAvailability(
    application: Application,
    paymentMethod: PaymentMethod,
    dropInConfiguration: DropInConfiguration,
    amount: Amount?,
    sessionDetails: SessionDetails?,
    callback: ComponentAvailableCallback,
) {
    try {
        Logger.v(TAG, "Checking availability for type - ${paymentMethod.type}")

        val type = paymentMethod.type ?: throw CheckoutException("PaymentMethod type is null")

        val availabilityCheck = getPaymentMethodAvailabilityCheck(dropInConfiguration, type, amount, sessionDetails)
        val configuration =
            getConfigurationForPaymentMethodOrNull<Configuration>(paymentMethod, dropInConfiguration, application)

        availabilityCheck.isAvailable(application, paymentMethod, configuration, callback)
    } catch (e: CheckoutException) {
        Logger.e(TAG, "Unable to initiate ${paymentMethod.type}", e)
        callback.onAvailabilityResult(false, paymentMethod)
    }
}

/**
 * Provides the [PaymentMethodAvailabilityCheck] class for the specified [paymentMethodType], if available.
 */
internal fun getPaymentMethodAvailabilityCheck(
    dropInConfiguration: DropInConfiguration,
    paymentMethodType: String,
    amount: Amount?,
    sessionDetails: SessionDetails?,
): PaymentMethodAvailabilityCheck<Configuration> {
    val dropInParams = dropInConfiguration.mapToParams(amount)
    val sessionParams = sessionDetails?.mapToParams(amount)

    @Suppress("UNCHECKED_CAST")
    val availabilityCheck = when (paymentMethodType) {
        PaymentMethodTypes.GOOGLE_PAY,
        PaymentMethodTypes.GOOGLE_PAY_LEGACY -> runCompileOnly {
            GooglePayComponentProvider(dropInParams, sessionParams)
        }

        PaymentMethodTypes.WECHAT_PAY_SDK -> runCompileOnly { WeChatPayProvider() }
        else -> AlwaysAvailablePaymentMethod()
    } as? PaymentMethodAvailabilityCheck<Configuration>

    return availabilityCheck ?: NotAvailablePaymentMethod()
}
