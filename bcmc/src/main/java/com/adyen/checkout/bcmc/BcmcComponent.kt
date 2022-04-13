/*
 * Copyright (c) 2019 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by arman on 18/9/2019.
 */
package com.adyen.checkout.bcmc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.adyen.checkout.card.CardValidationMapper
import com.adyen.checkout.card.CardValidationUtils
import com.adyen.checkout.card.api.model.Brand
import com.adyen.checkout.card.data.CardType
import com.adyen.checkout.card.data.ExpiryDate
import com.adyen.checkout.components.PaymentComponentProvider
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.base.BasePaymentComponent
import com.adyen.checkout.components.base.GenericPaymentMethodDelegate
import com.adyen.checkout.components.model.payments.request.CardPaymentMethod
import com.adyen.checkout.components.model.payments.request.PaymentComponentData
import com.adyen.checkout.components.repository.PublicKeyRepository
import com.adyen.checkout.components.ui.FieldState
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.exception.ComponentException
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.cse.CardEncrypter
import com.adyen.checkout.cse.UnencryptedCard
import com.adyen.checkout.cse.exception.EncryptionException
import com.adyen.threeds2.ThreeDS2Service
import kotlinx.coroutines.launch

/**
 * Constructs a [BcmcComponent] object.
 *
 * @param paymentMethodDelegate [GenericPaymentMethodDelegate] represents payment method.
 * @param configuration [BcmcConfiguration].
 */
class BcmcComponent(
    savedStateHandle: SavedStateHandle,
    paymentMethodDelegate: GenericPaymentMethodDelegate,
    configuration: BcmcConfiguration,
    private val publicKeyRepository: PublicKeyRepository,
    private val cardValidationMapper: CardValidationMapper
) : BasePaymentComponent<BcmcConfiguration, BcmcInputData, BcmcOutputData,
    PaymentComponentState<CardPaymentMethod>>(savedStateHandle, paymentMethodDelegate, configuration) {

    private var publicKey: String? = null

    init {
        viewModelScope.launch {
            try {
                publicKey = fetchPublicKey()
                notifyStateChanged()
            } catch (e: CheckoutException) {
                notifyException(ComponentException("Unable to fetch publicKey.", e))
            }
        }
    }

    override fun getSupportedPaymentMethodTypes(): Array<String> = PAYMENT_METHOD_TYPES

    private suspend fun fetchPublicKey(): String {
        return publicKeyRepository.fetchPublicKey(
            environment = configuration.environment,
            clientKey = configuration.clientKey
        )
    }

    override fun onInputDataChanged(inputData: BcmcInputData): BcmcOutputData {
        Logger.v(TAG, "onInputDataChanged")
        return BcmcOutputData(
            validateCardNumber(inputData.cardNumber),
            validateExpiryDate(inputData.expiryDate),
            inputData.isStorePaymentSelected
        )
    }

    @SuppressWarnings("ReturnCount")
    override fun createComponentState(): PaymentComponentState<CardPaymentMethod> {
        Logger.v(TAG, "createComponentState")

        val unencryptedCardBuilder = UnencryptedCard.Builder()
        val outputData = outputData
        val paymentComponentData = PaymentComponentData<CardPaymentMethod>()

        val publicKey = publicKey

        // If data is not valid we just return empty object, encryption would fail and we don't pass unencrypted data.
        if (outputData?.isValid != true || publicKey == null) {
            val isInputValid = outputData?.isValid ?: false
            val isReady = publicKey != null
            return PaymentComponentState(paymentComponentData, isInputValid, isReady)
        }
        val encryptedCard = try {
            unencryptedCardBuilder.setNumber(outputData.cardNumberField.value)
            val expiryDateResult = outputData.expiryDateField.value
            if (expiryDateResult != ExpiryDate.EMPTY_DATE) {
                unencryptedCardBuilder.setExpiryMonth(expiryDateResult.expiryMonth.toString())
                unencryptedCardBuilder.setExpiryYear(expiryDateResult.expiryYear.toString())
            }
            CardEncrypter.encryptFields(unencryptedCardBuilder.build(), publicKey)
        } catch (e: EncryptionException) {
            notifyException(e)
            return PaymentComponentState(paymentComponentData, isInputValid = false, isReady = true)
        }

        // BCMC payment method is scheme type.
        val cardPaymentMethod = CardPaymentMethod().apply {
            type = CardPaymentMethod.PAYMENT_METHOD_TYPE
            encryptedCardNumber = encryptedCard.encryptedCardNumber
            encryptedExpiryMonth = encryptedCard.encryptedExpiryMonth
            encryptedExpiryYear = encryptedCard.encryptedExpiryYear
            try {
                threeDS2SdkVersion = ThreeDS2Service.INSTANCE.sdkVersion
            } catch (e: ClassNotFoundException) {
                Logger.e(TAG, "threeDS2SdkVersion not set because 3DS2 SDK is not present in project.")
            } catch (e: NoClassDefFoundError) {
                Logger.e(TAG, "threeDS2SdkVersion not set because 3DS2 SDK is not present in project.")
            }
        }
        paymentComponentData.paymentMethod = cardPaymentMethod
        paymentComponentData.storePaymentMethod = outputData.isStoredPaymentMethodEnabled
        paymentComponentData.shopperReference = configuration.shopperReference
        return PaymentComponentState(paymentComponentData, isInputValid = true, isReady = true)
    }

    fun isCardNumberSupported(cardNumber: String?): Boolean {
        if (cardNumber.isNullOrEmpty()) return false
        return CardType.estimate(cardNumber).contains(SUPPORTED_CARD_TYPE)
    }

    private fun validateCardNumber(cardNumber: String): FieldState<String> {
        val validation =
            CardValidationUtils.validateCardNumber(cardNumber, enableLuhnCheck = true, isBrandSupported = true)
        return cardValidationMapper.mapCardNumberValidation(cardNumber, validation)
    }

    private fun validateExpiryDate(expiryDate: ExpiryDate): FieldState<ExpiryDate> {
        return CardValidationUtils.validateExpiryDate(expiryDate, Brand.FieldPolicy.REQUIRED)
    }

    companion object {
        private val TAG = LogUtil.getTag()

        private val PAYMENT_METHOD_TYPES = arrayOf(PaymentMethodTypes.BCMC)

        @JvmField
        val PROVIDER: PaymentComponentProvider<BcmcComponent, BcmcConfiguration> = BcmcComponentProvider()

        @JvmField
        val SUPPORTED_CARD_TYPE = CardType.BCMC
    }
}
