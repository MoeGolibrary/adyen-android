/*
 * Copyright (c) 2019 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by arman on 12/6/2019.
 */
package com.adyen.checkout.eps

import androidx.lifecycle.SavedStateHandle
import com.adyen.checkout.components.PaymentComponentProvider
import com.adyen.checkout.components.base.GenericPaymentComponentProvider
import com.adyen.checkout.components.base.GenericPaymentMethodDelegate
import com.adyen.checkout.components.model.payments.request.EPSPaymentMethod
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.issuerlist.IssuerListComponent

/**
 * PaymentComponent to handle iDeal payments.
 */
class EPSComponent(
    savedStateHandle: SavedStateHandle,
    paymentMethodDelegate: GenericPaymentMethodDelegate,
    configuration: EPSConfiguration
) : IssuerListComponent<EPSPaymentMethod>(savedStateHandle, paymentMethodDelegate, configuration) {

    override fun getSupportedPaymentMethodTypes(): Array<String> = PAYMENT_METHOD_TYPES

    override fun instantiateTypedPaymentMethod(): EPSPaymentMethod {
        return EPSPaymentMethod()
    }

    companion object {
        val PROVIDER: PaymentComponentProvider<EPSComponent, EPSConfiguration> = GenericPaymentComponentProvider(
            EPSComponent::class.java
        )
        val PAYMENT_METHOD_TYPES = arrayOf(PaymentMethodTypes.EPS)
    }
}