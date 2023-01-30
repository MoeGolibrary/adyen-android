/*
 * Copyright (c) 2019 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by arman on 12/6/2019.
 */
package com.adyen.checkout.openbanking

import com.adyen.checkout.action.DefaultActionHandlingComponent
import com.adyen.checkout.action.GenericActionDelegate
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.base.ComponentEventHandler
import com.adyen.checkout.components.model.payments.request.OpenBankingPaymentMethod
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.issuerlist.IssuerListComponent
import com.adyen.checkout.issuerlist.IssuerListDelegate
import com.adyen.checkout.openbanking.OpenBankingComponent.Companion.PROVIDER
import com.adyen.checkout.sessions.provider.SessionPaymentComponentProvider

/**
 * Component should not be instantiated directly. Instead use the [PROVIDER] object.
 */
class OpenBankingComponent internal constructor(
    delegate: IssuerListDelegate<OpenBankingPaymentMethod>,
    genericActionDelegate: GenericActionDelegate,
    actionHandlingComponent: DefaultActionHandlingComponent,
    componentEventHandler: ComponentEventHandler<PaymentComponentState<OpenBankingPaymentMethod>>,
) : IssuerListComponent<OpenBankingPaymentMethod>(
    delegate,
    genericActionDelegate,
    actionHandlingComponent,
    componentEventHandler,
) {
    companion object {
        @JvmField
        val PROVIDER: SessionPaymentComponentProvider<
            OpenBankingComponent,
            OpenBankingConfiguration,
            PaymentComponentState<OpenBankingPaymentMethod>
            > = OpenBankingComponentProvider()

        @JvmField
        val PAYMENT_METHOD_TYPES = listOf(PaymentMethodTypes.OPEN_BANKING)
    }
}
