/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by oscars on 11/7/2022.
 */

package com.adyen.checkout.issuerlist.internal.ui

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import com.adyen.checkout.components.PaymentComponentEvent
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.analytics.AnalyticsRepository
import com.adyen.checkout.components.model.paymentmethods.PaymentMethod
import com.adyen.checkout.components.model.payments.request.IssuerListPaymentMethod
import com.adyen.checkout.components.model.payments.request.Order
import com.adyen.checkout.components.model.payments.request.PaymentComponentData
import com.adyen.checkout.components.repository.PaymentObserverRepository
import com.adyen.checkout.components.ui.PaymentComponentUIEvent
import com.adyen.checkout.components.ui.PaymentComponentUIState
import com.adyen.checkout.components.ui.SubmitHandler
import com.adyen.checkout.components.ui.view.ButtonComponentViewType
import com.adyen.checkout.components.ui.view.ComponentViewType
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.issuerlist.IssuerListViewType
import com.adyen.checkout.issuerlist.internal.ui.model.IssuerListComponentParams
import com.adyen.checkout.issuerlist.internal.ui.model.IssuerListInputData
import com.adyen.checkout.issuerlist.internal.ui.model.IssuerListOutputData
import com.adyen.checkout.issuerlist.internal.ui.model.IssuerModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions", "LongParameterList")
internal class DefaultIssuerListDelegate<IssuerListPaymentMethodT : IssuerListPaymentMethod>(
    private val observerRepository: PaymentObserverRepository,
    override val componentParams: IssuerListComponentParams,
    private val paymentMethod: PaymentMethod,
    private val order: Order?,
    private val analyticsRepository: AnalyticsRepository,
    private val submitHandler: SubmitHandler<PaymentComponentState<IssuerListPaymentMethodT>>,
    private val typedPaymentMethodFactory: () -> IssuerListPaymentMethodT,
) : IssuerListDelegate<IssuerListPaymentMethodT> {

    private val inputData: IssuerListInputData = IssuerListInputData()

    private val _outputDataFlow = MutableStateFlow(createOutputData())
    override val outputDataFlow: Flow<IssuerListOutputData> = _outputDataFlow

    override val outputData: IssuerListOutputData get() = _outputDataFlow.value

    private val _componentStateFlow = MutableStateFlow(createComponentState())
    override val componentStateFlow: Flow<PaymentComponentState<IssuerListPaymentMethodT>> = _componentStateFlow

    private val _viewFlow: MutableStateFlow<ComponentViewType?> = MutableStateFlow(getIssuerListComponentViewType())
    override val viewFlow: Flow<ComponentViewType?> = _viewFlow

    override val submitFlow: Flow<PaymentComponentState<IssuerListPaymentMethodT>> = submitHandler.submitFlow
    override val uiStateFlow: Flow<PaymentComponentUIState> = submitHandler.uiStateFlow
    override val uiEventFlow: Flow<PaymentComponentUIEvent> = submitHandler.uiEventFlow

    override fun initialize(coroutineScope: CoroutineScope) {
        submitHandler.initialize(coroutineScope, componentStateFlow)
        sendAnalyticsEvent(coroutineScope)
    }

    private fun sendAnalyticsEvent(coroutineScope: CoroutineScope) {
        Logger.v(TAG, "sendAnalyticsEvent")
        coroutineScope.launch {
            analyticsRepository.sendAnalyticsEvent()
        }
    }

    override fun observe(
        lifecycleOwner: LifecycleOwner,
        coroutineScope: CoroutineScope,
        callback: (PaymentComponentEvent<PaymentComponentState<IssuerListPaymentMethodT>>) -> Unit
    ) {
        observerRepository.addObservers(
            stateFlow = componentStateFlow,
            exceptionFlow = null,
            submitFlow = submitFlow,
            lifecycleOwner = lifecycleOwner,
            coroutineScope = coroutineScope,
            callback = callback
        )
    }

    override fun removeObserver() {
        observerRepository.removeObservers()
    }

    private fun getIssuerListComponentViewType(): IssuerListComponentViewType {
        return when (componentParams.viewType) {
            IssuerListViewType.RECYCLER_VIEW -> IssuerListComponentViewType.RecyclerView
            IssuerListViewType.SPINNER_VIEW -> IssuerListComponentViewType.SpinnerView
        }
    }

    override fun getIssuers(): List<IssuerModel> =
        paymentMethod.issuers?.mapToModel(componentParams.environment) ?: paymentMethod.details.getLegacyIssuers(
            componentParams.environment
        )

    override fun updateInputData(update: IssuerListInputData.() -> Unit) {
        inputData.update()
        onInputDataChanged()
    }

    override fun onSubmit() {
        val state = _componentStateFlow.value
        submitHandler.onSubmit(state)
    }

    private fun onInputDataChanged() {
        val outputData = createOutputData()

        _outputDataFlow.tryEmit(outputData)

        updateComponentState(outputData)
    }

    private fun createOutputData() = IssuerListOutputData(inputData.selectedIssuer)

    @VisibleForTesting
    internal fun updateComponentState(outputData: IssuerListOutputData) {
        val componentState = createComponentState(outputData)
        _componentStateFlow.tryEmit(componentState)
    }

    private fun createComponentState(
        outputData: IssuerListOutputData = this.outputData
    ): PaymentComponentState<IssuerListPaymentMethodT> {
        val issuerListPaymentMethod = typedPaymentMethodFactory().apply {
            type = getPaymentMethodType()
            issuer = outputData.selectedIssuer?.id.orEmpty()
        }

        val paymentComponentData = PaymentComponentData(
            paymentMethod = issuerListPaymentMethod,
            order = order,
        )

        return PaymentComponentState(paymentComponentData, outputData.isValid, true)
    }

    override fun getPaymentMethodType(): String {
        return paymentMethod.type ?: PaymentMethodTypes.UNKNOWN
    }

    override fun isConfirmationRequired(): Boolean = _viewFlow.value is ButtonComponentViewType

    override fun shouldShowSubmitButton(): Boolean = isConfirmationRequired() && componentParams.isSubmitButtonVisible

    override fun setInteractionBlocked(isInteractionBlocked: Boolean) {
        submitHandler.setInteractionBlocked(isInteractionBlocked)
    }

    override fun onCleared() {
        removeObserver()
    }

    companion object {
        private val TAG = LogUtil.getTag()
    }
}