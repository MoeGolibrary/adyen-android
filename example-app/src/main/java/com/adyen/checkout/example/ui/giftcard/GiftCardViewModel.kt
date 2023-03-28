package com.adyen.checkout.example.ui.giftcard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adyen.checkout.components.core.ActionComponentData
import com.adyen.checkout.components.core.BalanceResult
import com.adyen.checkout.components.core.ComponentError
import com.adyen.checkout.components.core.Order
import com.adyen.checkout.components.core.OrderRequest
import com.adyen.checkout.components.core.OrderResponse
import com.adyen.checkout.components.core.PaymentComponentData
import com.adyen.checkout.components.core.action.Action
import com.adyen.checkout.components.core.internal.util.StatusResponseUtils
import com.adyen.checkout.components.core.paymentmethod.PaymentMethodDetails
import com.adyen.checkout.core.internal.data.model.getStringOrNull
import com.adyen.checkout.core.internal.data.model.toStringPretty
import com.adyen.checkout.core.internal.util.LogUtil
import com.adyen.checkout.core.internal.util.Logger
import com.adyen.checkout.example.data.storage.KeyValueStorage
import com.adyen.checkout.example.repositories.PaymentsRepository
import com.adyen.checkout.example.service.createBalanceRequest
import com.adyen.checkout.example.service.createOrderRequest
import com.adyen.checkout.example.service.createPaymentRequest
import com.adyen.checkout.example.service.getPaymentMethodRequest
import com.adyen.checkout.example.ui.card.CardActivity
import com.adyen.checkout.giftcard.GiftCardComponent
import com.adyen.checkout.giftcard.GiftCardComponentCallback
import com.adyen.checkout.giftcard.GiftCardComponentState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions")
internal class GiftCardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val paymentsRepository: PaymentsRepository,
    private val keyValueStorage: KeyValueStorage,
) : ViewModel(), GiftCardComponentCallback {

    private val _giftCardComponentDataFlow = MutableStateFlow<GiftCardComponentData?>(null)
    val giftCardComponentDataFlow: Flow<GiftCardComponentData> = _giftCardComponentDataFlow.filterNotNull()

    private val _giftCardViewStateFlow = MutableStateFlow<GiftCardViewState>(GiftCardViewState.Loading)
    internal val giftCardViewStateFlow: Flow<GiftCardViewState> = _giftCardViewStateFlow

    private val _events = MutableSharedFlow<GiftCardEvent>()
    internal val events: Flow<GiftCardEvent> = _events

    private var order: OrderRequest? = null

    init {
        viewModelScope.launch { fetchPaymentMethods() }
    }

    private suspend fun fetchPaymentMethods() = withContext(Dispatchers.IO) {
        val paymentMethodResponse = paymentsRepository.getPaymentMethods(
            getPaymentMethodRequest(
                merchantAccount = keyValueStorage.getMerchantAccount(),
                shopperReference = keyValueStorage.getShopperReference(),
                amount = keyValueStorage.getAmount(),
                countryCode = keyValueStorage.getCountry(),
                shopperLocale = keyValueStorage.getShopperLocale(),
                splitCardFundingSources = keyValueStorage.isSplitCardFundingSources()
            )
        )

        val giftCardPaymentMethod = paymentMethodResponse
            ?.paymentMethods
            ?.firstOrNull { GiftCardComponent.PROVIDER.isPaymentMethodSupported(it) }

        if (giftCardPaymentMethod == null) {
            _giftCardViewStateFlow.emit(GiftCardViewState.Error)
        } else {
            _giftCardComponentDataFlow.emit(
                GiftCardComponentData(
                    paymentMethod = giftCardPaymentMethod,
                    callback = this@GiftCardViewModel,
                )
            )
            _giftCardViewStateFlow.emit(GiftCardViewState.ShowComponent)
        }
    }

    override fun onSubmit(state: GiftCardComponentState) {
        makePayment(state.data)
    }

    override fun onAdditionalDetails(actionComponentData: ActionComponentData) {
        sendPaymentDetails(actionComponentData)
    }

    override fun onError(componentError: ComponentError) {
        onComponentError(componentError)
    }

    // no ops
    override fun onStateChanged(state: GiftCardComponentState) = Unit

    override fun onBalanceCheck(paymentMethodDetails: PaymentMethodDetails) {
        viewModelScope.launch(Dispatchers.IO) {
            Logger.d(TAG, "checkBalance")

            val paymentMethodJson = PaymentMethodDetails.SERIALIZER.serialize(paymentMethodDetails)
            Logger.v(TAG, "paymentMethods/balance/ - ${paymentMethodJson.toStringPretty()}")

            val request = createBalanceRequest(
                paymentMethodJson,
                keyValueStorage.getMerchantAccount()
            )

            val response = paymentsRepository.getBalance(request)
            handleBalanceResponse(response)
        }
    }

    private fun handleBalanceResponse(jsonResponse: JSONObject?) {
        if (jsonResponse != null) {
            when (val resultCode = jsonResponse.getStringOrNull("resultCode")) {
                "Success" -> {
                    viewModelScope.launch {
                        _events.emit(
                            GiftCardEvent.Balance(
                                BalanceResult.SERIALIZER.deserialize(
                                    jsonResponse
                                )
                            )
                        )
                    }
                }
                "NotEnoughBalance" -> viewModelScope.launch { _giftCardViewStateFlow.emit(GiftCardViewState.Error) }
                else -> viewModelScope.launch { _giftCardViewStateFlow.emit(GiftCardViewState.Error) }
            }
        } else {
            Logger.e(TAG, "FAILED")
        }
    }

    override fun onRequestOrder() {
        viewModelScope.launch(Dispatchers.IO) {
            Logger.d(TAG, "createOrder")

            val paymentRequest = createOrderRequest(
                keyValueStorage.getAmount(),
                keyValueStorage.getMerchantAccount()
            )

            val response = paymentsRepository.createOrder(paymentRequest)

            handleOrderResponse(response)
        }
    }

    private fun handleOrderResponse(jsonResponse: JSONObject?) {
        if (jsonResponse != null) {
            when (val resultCode = jsonResponse.getStringOrNull("resultCode")) {
                "Success" -> viewModelScope.launch {
                    val orderResponse = OrderResponse.SERIALIZER.deserialize(jsonResponse)
                    _events.emit(GiftCardEvent.OrderCreated(orderResponse))
                    order = OrderRequest(
                        orderData = orderResponse.orderData,
                        pspReference = orderResponse.pspReference
                    )
                }
                else -> viewModelScope.launch { _giftCardViewStateFlow.emit(GiftCardViewState.Error) }
            }
        } else {
            Logger.e(TAG, "FAILED")
            viewModelScope.launch { _giftCardViewStateFlow.emit(GiftCardViewState.Error) }
        }
    }

    private fun makePayment(data: PaymentComponentData<*>) {
        _giftCardViewStateFlow.value = GiftCardViewState.Loading

        val paymentComponentData = PaymentComponentData.SERIALIZER.serialize(data)

        viewModelScope.launch(Dispatchers.IO) {
            val paymentRequest = createPaymentRequest(
                paymentComponentData = paymentComponentData,
                shopperReference = keyValueStorage.getShopperReference(),
                amount = keyValueStorage.getAmount(),
                countryCode = keyValueStorage.getCountry(),
                merchantAccount = keyValueStorage.getMerchantAccount(),
                redirectUrl = savedStateHandle.get<String>(CardActivity.RETURN_URL_EXTRA)
                    ?: throw IllegalStateException("Return url should be set"),
                isThreeds2Enabled = keyValueStorage.isThreeds2Enable(),
                isExecuteThreeD = keyValueStorage.isExecuteThreeD()
            )

            handlePaymentResponse(paymentsRepository.makePaymentsRequest(paymentRequest))
        }
    }

    private suspend fun handlePaymentResponse(json: JSONObject?) {
        viewModelScope.launch { _giftCardViewStateFlow.emit(GiftCardViewState.ShowComponent) }
        json?.let {
            when {
                json.has("action") -> {
                    val action = Action.SERIALIZER.deserialize(json.getJSONObject("action"))
                    handleAction(action)
                }
                isRefusedInPartialPaymentFlow(json) -> {
                    _events.emit(GiftCardEvent.PaymentResult("Refused in Partial Payment Flow"))
                }
                isNonFullyPaidOrder(json) -> {
                    order = getOrderFromResponse(json).let {
                        Order(
                            pspReference = it.pspReference,
                            orderData = it.orderData
                        )
                    }
                }
                else -> _events.emit(GiftCardEvent.PaymentResult("Success: ${json.getStringOrNull("resultCode")}"))
            }
        } ?: _events.emit(GiftCardEvent.PaymentResult("Failed"))
    }

    private fun isRefusedInPartialPaymentFlow(jsonResponse: JSONObject) =
        isRefused(jsonResponse) && isNonFullyPaidOrder(jsonResponse)

    private fun isRefused(jsonResponse: JSONObject): Boolean {
        return jsonResponse.getStringOrNull("resultCode")
            .equals(other = StatusResponseUtils.RESULT_REFUSED, ignoreCase = true)
    }

    private fun isNonFullyPaidOrder(jsonResponse: JSONObject): Boolean {
        return jsonResponse.has("order") && (getOrderFromResponse(jsonResponse).remainingAmount?.value ?: 0) > 0
    }

    private fun getOrderFromResponse(jsonResponse: JSONObject): OrderResponse {
        val orderJSON = jsonResponse.getJSONObject("order")
        return OrderResponse.SERIALIZER.deserialize(orderJSON)
    }

    private fun handleAction(action: Action) {
        viewModelScope.launch { _events.emit(GiftCardEvent.AdditionalAction(action)) }
    }

    private fun sendPaymentDetails(actionComponentData: ActionComponentData) {
        viewModelScope.launch(Dispatchers.IO) {
            val json = ActionComponentData.SERIALIZER.serialize(actionComponentData)
            handlePaymentResponse(paymentsRepository.makeDetailsRequest(json))
        }
    }

    private fun onComponentError(error: ComponentError) {
        viewModelScope.launch { _events.emit(GiftCardEvent.PaymentResult("Failed: ${error.errorMessage}")) }
    }

    fun reloadComponentWithOrder() {
        val order = this.order
        val giftCardComponentData = _giftCardComponentDataFlow.value
        if (order != null && giftCardComponentData != null) {
            viewModelScope.launch {
                _events.emit(
                    GiftCardEvent.ReloadComponent(
                        order,
                        giftCardComponentData
                    )
                )
            }
        }
    }

    companion object {
        private val TAG = LogUtil.getTag()
    }
}