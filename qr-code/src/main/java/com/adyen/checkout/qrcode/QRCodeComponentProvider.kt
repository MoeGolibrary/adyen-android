/*
 * Copyright (c) 2021 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 7/6/2021.
 */

package com.adyen.checkout.qrcode

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.adyen.checkout.components.ActionComponentProvider
import com.adyen.checkout.components.base.lifecycle.viewModelFactory
import com.adyen.checkout.components.model.payments.response.Action
import com.adyen.checkout.components.model.payments.response.QrCodeAction
import com.adyen.checkout.components.repository.PaymentDataRepository
import com.adyen.checkout.components.status.DefaultStatusRepository
import com.adyen.checkout.components.status.api.StatusService
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.redirect.handler.DefaultRedirectHandler

private val VIEWABLE_PAYMENT_METHODS = listOf(PaymentMethodTypes.PIX)

class QRCodeComponentProvider : ActionComponentProvider<QRCodeComponent, QRCodeConfiguration> {
    override fun <T> get(
        owner: T,
        application: Application,
        configuration: QRCodeConfiguration
    ): QRCodeComponent where T : SavedStateRegistryOwner, T : ViewModelStoreOwner {
        return get(owner, owner, application, configuration, null)
    }

    override fun get(
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        application: Application,
        configuration: QRCodeConfiguration,
        defaultArgs: Bundle?
    ): QRCodeComponent {
        val statusService = StatusService(configuration.environment.baseUrl)
        val statusRepository = DefaultStatusRepository(statusService, configuration.clientKey)
        val countDownTimer = QRCodeCountDownTimer()
        val redirectHandler = DefaultRedirectHandler()
        val qrCodeFactory = viewModelFactory(savedStateRegistryOwner, defaultArgs) { savedStateHandle ->
            val paymentDataRepository = PaymentDataRepository(savedStateHandle)
            val qrCodeDelegate = DefaultQRCodeDelegate(
                statusRepository,
                countDownTimer,
                redirectHandler,
                paymentDataRepository
            )
            QRCodeComponent(
                savedStateHandle = savedStateHandle,
                application = application,
                configuration = configuration,
                qrCodeDelegate = qrCodeDelegate,
            )
        }
        return ViewModelProvider(viewModelStoreOwner, qrCodeFactory).get(QRCodeComponent::class.java)
    }

    override val supportedActionTypes: List<String>
        get() = listOf(QrCodeAction.ACTION_TYPE)

    @Deprecated(
        message = "You can safely remove this method, it will always return true as all action components require" +
            " a configuration.",
        replaceWith = ReplaceWith("true")
    )
    override fun requiresConfiguration(): Boolean = true

    override fun canHandleAction(action: Action): Boolean {
        return when {
            !supportedActionTypes.contains(action.type) -> false
            // viewable action, can be handled
            requiresView(action) -> true
            // QR code actions that contain a url are handled as a redirect action
            !(action as? QrCodeAction)?.url.isNullOrEmpty() -> true
            else -> false
        }
    }

    // TODO remove this method when we create a generic Action handling Component
    override fun requiresView(action: Action): Boolean {
        return VIEWABLE_PAYMENT_METHODS.contains(action.paymentMethodType)
    }

    override fun providesDetails(): Boolean {
        return true
    }
}
