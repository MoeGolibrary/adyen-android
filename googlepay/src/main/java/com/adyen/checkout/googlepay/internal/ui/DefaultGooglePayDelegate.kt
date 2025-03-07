/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by oscars on 19/7/2022.
 */

package com.adyen.checkout.googlepay.internal.ui

import android.app.Activity
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import com.adyen.checkout.components.core.OrderRequest
import com.adyen.checkout.components.core.PaymentComponentData
import com.adyen.checkout.components.core.PaymentMethod
import com.adyen.checkout.components.core.PaymentMethodTypes
import com.adyen.checkout.components.core.internal.PaymentComponentEvent
import com.adyen.checkout.components.core.internal.PaymentObserverRepository
import com.adyen.checkout.components.core.internal.analytics.AnalyticsManager
import com.adyen.checkout.components.core.internal.analytics.ErrorEvent
import com.adyen.checkout.components.core.internal.analytics.GenericEvents
import com.adyen.checkout.components.core.internal.util.bufferedChannel
import com.adyen.checkout.core.AdyenLogLevel
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.exception.ComponentException
import com.adyen.checkout.core.internal.data.model.ModelUtils
import com.adyen.checkout.core.internal.util.adyenLog
import com.adyen.checkout.googlepay.GooglePayButtonParameters
import com.adyen.checkout.googlepay.GooglePayComponentState
import com.adyen.checkout.googlepay.internal.data.model.GooglePayPaymentMethodModel
import com.adyen.checkout.googlepay.internal.ui.model.GooglePayComponentParams
import com.adyen.checkout.googlepay.internal.util.GooglePayUtils
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

@Suppress("TooManyFunctions")
internal class DefaultGooglePayDelegate(
    private val observerRepository: PaymentObserverRepository,
    private val paymentMethod: PaymentMethod,
    private val order: OrderRequest?,
    override val componentParams: GooglePayComponentParams,
    private val analyticsManager: AnalyticsManager,
) : GooglePayDelegate {

    private val _componentStateFlow = MutableStateFlow(createComponentState())
    override val componentStateFlow: Flow<GooglePayComponentState> = _componentStateFlow

    private val exceptionChannel: Channel<CheckoutException> = bufferedChannel()
    override val exceptionFlow: Flow<CheckoutException> = exceptionChannel.receiveAsFlow()

    private val submitChannel: Channel<GooglePayComponentState> = bufferedChannel()
    override val submitFlow: Flow<GooglePayComponentState> = submitChannel.receiveAsFlow()

    override fun initialize(coroutineScope: CoroutineScope) {
        initializeAnalytics(coroutineScope)

        componentStateFlow.onEach {
            onState(it)
        }.launchIn(coroutineScope)
    }

    private fun initializeAnalytics(coroutineScope: CoroutineScope) {
        adyenLog(AdyenLogLevel.VERBOSE) { "initializeAnalytics" }
        analyticsManager.initialize(this, coroutineScope)

        val event = GenericEvents.rendered(paymentMethod.type.orEmpty())
        analyticsManager.trackEvent(event)
    }

    private fun onState(state: GooglePayComponentState) {
        if (state.isValid) {
            val event = GenericEvents.submit(paymentMethod.type.orEmpty())
            analyticsManager.trackEvent(event)

            submitChannel.trySend(state)
        }
    }

    override fun observe(
        lifecycleOwner: LifecycleOwner,
        coroutineScope: CoroutineScope,
        callback: (PaymentComponentEvent<GooglePayComponentState>) -> Unit
    ) {
        observerRepository.addObservers(
            stateFlow = componentStateFlow,
            exceptionFlow = exceptionFlow,
            submitFlow = submitFlow,
            lifecycleOwner = lifecycleOwner,
            coroutineScope = coroutineScope,
            callback = callback,
        )
    }

    override fun removeObserver() {
        observerRepository.removeObservers()
    }

    @VisibleForTesting
    internal fun updateComponentState(paymentData: PaymentData?) {
        adyenLog(AdyenLogLevel.VERBOSE) { "updateComponentState" }
        val componentState = createComponentState(paymentData)
        _componentStateFlow.tryEmit(componentState)
    }

    private fun createComponentState(paymentData: PaymentData? = null): GooglePayComponentState {
        val isValid = paymentData?.let {
            GooglePayUtils.findToken(it).isNotEmpty()
        } ?: false

        val paymentMethod = GooglePayUtils.createGooglePayPaymentMethod(
            paymentData = paymentData,
            paymentMethodType = paymentMethod.type,
            checkoutAttemptId = analyticsManager.getCheckoutAttemptId(),
        )
        val paymentComponentData = PaymentComponentData(
            paymentMethod = paymentMethod,
            order = order,
            amount = componentParams.amount,
        )

        return GooglePayComponentState(
            data = paymentComponentData,
            isInputValid = isValid,
            isReady = true,
            paymentData = paymentData,
        )
    }

    override fun startGooglePayScreen(activity: Activity, requestCode: Int) {
        adyenLog(AdyenLogLevel.DEBUG) { "startGooglePayScreen" }
        val paymentsClient = Wallet.getPaymentsClient(activity, GooglePayUtils.createWalletOptions(componentParams))
        val paymentDataRequest = GooglePayUtils.createPaymentDataRequest(componentParams)
        // TODO this forces us to use the deprecated onActivityResult. Look into alternatives when/if Google provides
        //  any later.
        AutoResolveHelper.resolveTask(paymentsClient.loadPaymentData(paymentDataRequest), activity, requestCode)
    }

    override fun handleActivityResult(resultCode: Int, data: Intent?) {
        adyenLog(AdyenLogLevel.DEBUG) { "handleActivityResult" }
        when (resultCode) {
            Activity.RESULT_OK -> {
                if (data == null) {
                    trackThirdPartyErrorEvent()
                    exceptionChannel.trySend(ComponentException("Result data is null"))
                    return
                }
                val paymentData = PaymentData.getFromIntent(data)
                updateComponentState(paymentData)
            }

            Activity.RESULT_CANCELED -> {
                exceptionChannel.trySend(ComponentException("Payment canceled."))
            }

            AutoResolveHelper.RESULT_ERROR -> {
                trackThirdPartyErrorEvent()

                val status = AutoResolveHelper.getStatusFromIntent(data)
                val statusMessage: String = status?.let { ": ${it.statusMessage}" }.orEmpty()
                exceptionChannel.trySend(ComponentException("GooglePay returned an error$statusMessage"))
            }

            else -> Unit
        }
    }

    private fun trackThirdPartyErrorEvent() {
        val event = GenericEvents.error(
            component = getPaymentMethodType(),
            event = ErrorEvent.THIRD_PARTY,
        )
        analyticsManager.trackEvent(event)
    }

    override fun getGooglePayButtonParameters(): GooglePayButtonParameters {
        val allowedPaymentMethodsList = GooglePayUtils.getAllowedPaymentMethods(componentParams)
        val allowedPaymentMethods = ModelUtils.serializeOptList(
            allowedPaymentMethodsList,
            GooglePayPaymentMethodModel.SERIALIZER,
        )?.toString().orEmpty()
        return GooglePayButtonParameters(allowedPaymentMethods)
    }

    override fun getPaymentMethodType(): String {
        return paymentMethod.type ?: PaymentMethodTypes.UNKNOWN
    }

    override fun onCleared() {
        removeObserver()
        analyticsManager.clear(this)
    }
}
