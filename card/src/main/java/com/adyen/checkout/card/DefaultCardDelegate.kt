/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by ozgur on 26/7/2022.
 */

package com.adyen.checkout.card

import android.util.Log
import com.adyen.checkout.card.api.model.Brand
import com.adyen.checkout.card.data.CardType
import com.adyen.checkout.card.data.DetectedCardType
import com.adyen.checkout.card.data.ExpiryDate
import com.adyen.checkout.card.delegate.AddressDelegate
import com.adyen.checkout.card.delegate.DetectCardTypeDelegate
import com.adyen.checkout.card.ui.model.AddressListItem
import com.adyen.checkout.card.util.AddressFormUtils
import com.adyen.checkout.card.util.AddressValidationUtils
import com.adyen.checkout.card.util.CardValidationUtils
import com.adyen.checkout.card.util.DualBrandedCardUtils
import com.adyen.checkout.card.util.InstallmentUtils
import com.adyen.checkout.card.util.KcpValidationUtils
import com.adyen.checkout.card.util.SocialSecurityNumberUtils
import com.adyen.checkout.components.base.AddressVisibility
import com.adyen.checkout.components.model.paymentmethods.PaymentMethod
import com.adyen.checkout.components.model.payments.request.CardPaymentMethod
import com.adyen.checkout.components.model.payments.request.PaymentComponentData
import com.adyen.checkout.components.repository.PublicKeyRepository
import com.adyen.checkout.components.ui.FieldState
import com.adyen.checkout.components.ui.Validation
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.exception.ComponentException
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.cse.CardEncrypter
import com.adyen.checkout.cse.EncryptedCard
import com.adyen.checkout.cse.GenericEncrypter
import com.adyen.checkout.cse.UnencryptedCard
import com.adyen.checkout.cse.exception.EncryptionException
import com.adyen.threeds2.ThreeDS2Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Suppress("LongParameterList", "TooManyFunctions")
class DefaultCardDelegate(
    private val publicKeyRepository: PublicKeyRepository,
    private val configuration: CardConfiguration,
    private val paymentMethod: PaymentMethod,
    private val addressDelegate: AddressDelegate,
    private val detectCardTypeDelegate: DetectCardTypeDelegate,
    private val cardValidationMapper: CardValidationMapper,
    private val cardEncrypter: CardEncrypter
) : CardDelegate {

    override val inputData: CardInputData = CardInputData()

    private var publicKey: String? = null

    private val _outputDataFlow = MutableStateFlow<CardOutputData?>(null)
    override val outputDataFlow: Flow<CardOutputData?> = _outputDataFlow

    private val outputData
        get() = _outputDataFlow.value

    private val _componentStateFlow = MutableStateFlow<CardComponentState?>(null)
    override val componentStateFlow: Flow<CardComponentState?> = _componentStateFlow

    private val _exceptionFlow = MutableSharedFlow<CheckoutException>(0, 1, BufferOverflow.DROP_OLDEST)
    override val exceptionFlow: Flow<CheckoutException> = _exceptionFlow

    private var coroutineScope: CoroutineScope? = null

    override fun initialize(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope

        fetchPublicKey()
        subscribeToDetectedCardTypes()

        if (configuration.addressConfiguration is AddressConfiguration.FullAddress) {
            Log.e(TAG, "subscribe to states list")
            subscribeToStatesList()
            subscribeToCountryList()
            requestCountryList()
        }
    }

    private fun fetchPublicKey() {
        coroutineScope?.launch {
            publicKeyRepository.fetchPublicKey(
                environment = configuration.environment,
                clientKey = configuration.clientKey
            ).fold(
                onSuccess = { key ->
                    publicKey = key
                    outputData?.let { createComponentState(it) }
                },
                onFailure = { e ->
                    _exceptionFlow.tryEmit(ComponentException("Unable to fetch publicKey.", e))
                }
            )
        }
    }

    override fun onInputDataChanged(inputData: CardInputData) {
        val coroutineScope = coroutineScope ?: return
        detectCardTypeDelegate.detectCardType(
            cardNumber = inputData.cardNumber,
            publicKey = publicKey,
            supportedCardTypes = configuration.supportedCardTypes,
            environment = configuration.environment,
            clientKey = configuration.clientKey,
            coroutineScope = coroutineScope
        )
        requestStateList(inputData.address.country)
        val outputData = makeOutputData(
            cardNumber = inputData.cardNumber,
            expiryDate = inputData.expiryDate,
            securityCode = inputData.securityCode,
            holderName = inputData.holderName,
            socialSecurityNumber = inputData.socialSecurityNumber,
            kcpBirthDateOrTaxNumber = inputData.kcpBirthDateOrTaxNumber,
            kcpCardPassword = inputData.kcpCardPassword,
            addressInputModel = inputData.address,
            isStorePaymentSelected = inputData.isStorePaymentSelected,
            detectedCardTypes = outputData?.detectedCardTypes.orEmpty(),
            selectedCardIndex = inputData.selectedCardIndex,
            selectedInstallmentOption = inputData.installmentOption,
            countryOptions = AddressFormUtils.markAddressListItemSelected(
                outputData?.countryOptions.orEmpty(),
                inputData.address.country
            ),
            stateOptions = AddressFormUtils.markAddressListItemSelected(
                outputData?.stateOptions.orEmpty(),
                inputData.address.stateOrProvince
            )
        )
        _outputDataFlow.tryEmit(outputData)
        createComponentState(outputData)
    }

    private fun subscribeToDetectedCardTypes() {
        val coroutineScope = coroutineScope ?: return
        detectCardTypeDelegate.detectedCardTypesFlow
            .onEach {
                Logger.d(TAG, "New binLookupFlow emitted")
                Logger.d(TAG, "Brands: $it")
                with(outputData) {
                    this ?: return@with
                    val newOutputData = makeOutputData(
                        cardNumber = cardNumberState.value,
                        expiryDate = expiryDateState.value,
                        securityCode = securityCodeState.value,
                        holderName = holderNameState.value,
                        socialSecurityNumber = socialSecurityNumberState.value,
                        kcpBirthDateOrTaxNumber = kcpBirthDateOrTaxNumberState.value,
                        kcpCardPassword = kcpCardPasswordState.value,
                        addressInputModel = inputData.address,
                        isStorePaymentSelected = isStoredPaymentMethodEnable,
                        detectedCardTypes = it,
                        selectedCardIndex = inputData.selectedCardIndex,
                        selectedInstallmentOption = inputData.installmentOption,
                        countryOptions = countryOptions,
                        stateOptions = stateOptions
                    )
                    _outputDataFlow.tryEmit(newOutputData)
                    createComponentState(newOutputData)
                }
            }
            .launchIn(coroutineScope)
    }

    private fun subscribeToCountryList() {
        val coroutineScope = coroutineScope ?: return
        addressDelegate.countriesFlow
            .distinctUntilChanged()
            .onEach { countries ->
                val countryOptions = AddressFormUtils.initializeCountryOptions(
                    addressConfiguration = configuration.addressConfiguration,
                    countryList = countries
                )
                countryOptions.firstOrNull { it.selected }?.let {
                    inputData.address.country = it.code
                    requestStateList(it.code)
                }
                with(outputData) {
                    this ?: return@with
                    val newOutputData = makeOutputData(
                        cardNumber = cardNumberState.value,
                        expiryDate = expiryDateState.value,
                        securityCode = securityCodeState.value,
                        holderName = holderNameState.value,
                        socialSecurityNumber = socialSecurityNumberState.value,
                        kcpBirthDateOrTaxNumber = kcpBirthDateOrTaxNumberState.value,
                        kcpCardPassword = kcpCardPasswordState.value,
                        addressInputModel = inputData.address,
                        isStorePaymentSelected = isStoredPaymentMethodEnable,
                        detectedCardTypes = this.detectedCardTypes,
                        selectedCardIndex = inputData.selectedCardIndex,
                        selectedInstallmentOption = inputData.installmentOption,
                        countryOptions = countryOptions,
                        stateOptions = stateOptions
                    )
                    _outputDataFlow.tryEmit(newOutputData)
                    createComponentState(newOutputData)
                }
            }
            .launchIn(coroutineScope)
    }

    private fun subscribeToStatesList() {
        val coroutineScope = coroutineScope ?: return
        addressDelegate.statesFlow
            .distinctUntilChanged()
            .onEach {
                Logger.d(TAG, "New states emitted")
                Logger.d(TAG, "States: $it")
                with(outputData) {
                    this ?: return@with
                    val newOutputData = makeOutputData(
                        cardNumber = cardNumberState.value,
                        expiryDate = expiryDateState.value,
                        securityCode = securityCodeState.value,
                        holderName = holderNameState.value,
                        socialSecurityNumber = socialSecurityNumberState.value,
                        kcpBirthDateOrTaxNumber = kcpBirthDateOrTaxNumberState.value,
                        kcpCardPassword = kcpCardPasswordState.value,
                        addressInputModel = inputData.address,
                        isStorePaymentSelected = isStoredPaymentMethodEnable,
                        detectedCardTypes = detectedCardTypes,
                        selectedCardIndex = inputData.selectedCardIndex,
                        selectedInstallmentOption = inputData.installmentOption,
                        countryOptions = countryOptions,
                        stateOptions = AddressFormUtils.initializeStateOptions(it)
                    )
                    _outputDataFlow.tryEmit(newOutputData)
                    createComponentState(newOutputData)
                }
            }
            .launchIn(coroutineScope)
    }

    @Suppress("LongParameterList")
    private fun makeOutputData(
        cardNumber: String,
        expiryDate: ExpiryDate,
        securityCode: String,
        holderName: String,
        socialSecurityNumber: String,
        kcpBirthDateOrTaxNumber: String,
        kcpCardPassword: String,
        addressInputModel: AddressInputModel,
        isStorePaymentSelected: Boolean,
        detectedCardTypes: List<DetectedCardType>,
        selectedCardIndex: Int,
        selectedInstallmentOption: InstallmentModel?,
        countryOptions: List<AddressListItem>,
        stateOptions: List<AddressListItem>
    ): CardOutputData {

        val isReliable = detectedCardTypes.any { it.isReliable }
        val supportedCardTypes = detectedCardTypes.filter { it.isSupported }
        val sortedCardTypes = DualBrandedCardUtils.sortBrands(supportedCardTypes)
        val outputCardTypes = markSelectedCard(sortedCardTypes, selectedCardIndex)

        val selectedOrFirstCardType = outputCardTypes.firstOrNull { it.isSelected } ?: outputCardTypes.firstOrNull()

        // perform a Luhn Check if no brands are detected
        val enableLuhnCheck = selectedOrFirstCardType?.enableLuhnCheck ?: true

        // when no supported cards are detected, only show an error if the brand detection was reliable
        val shouldFailWithUnsupportedBrand = selectedOrFirstCardType == null && isReliable

        val addressFormUIState = getAddressFormUIState(
            configuration.addressConfiguration,
            configuration.addressVisibility
        )

        return CardOutputData(
            cardNumberState = validateCardNumber(
                cardNumber,
                enableLuhnCheck,
                isBrandSupported = !shouldFailWithUnsupportedBrand
            ),
            expiryDateState = validateExpiryDate(expiryDate, selectedOrFirstCardType?.expiryDatePolicy),
            securityCodeState = validateSecurityCode(securityCode, selectedOrFirstCardType),
            holderNameState = validateHolderName(holderName),
            socialSecurityNumberState = validateSocialSecurityNumber(socialSecurityNumber),
            kcpBirthDateOrTaxNumberState = validateKcpBirthDateOrTaxNumber(kcpBirthDateOrTaxNumber),
            kcpCardPasswordState = validateKcpCardPassword(kcpCardPassword),
            addressState = validateAddress(addressInputModel, addressFormUIState),
            installmentState = makeInstallmentFieldState(selectedInstallmentOption),
            isStoredPaymentMethodEnable = isStorePaymentSelected,
            cvcUIState = makeCvcUIState(selectedOrFirstCardType?.cvcPolicy),
            expiryDateUIState = makeExpiryDateUIState(selectedOrFirstCardType?.expiryDatePolicy),
            detectedCardTypes = outputCardTypes,
            isSocialSecurityNumberRequired = isSocialSecurityNumberRequired(),
            isKCPAuthRequired = isKCPAuthRequired(),
            addressUIState = addressFormUIState,
            installmentOptions = getInstallmentOptions(
                configuration.installmentConfiguration,
                selectedOrFirstCardType?.cardType,
                isReliable
            ),
            countryOptions = countryOptions,
            stateOptions = stateOptions,
            supportedCardTypes = getSupportedCardTypes(),
        )
    }

    override fun getPaymentMethodType(): String {
        return paymentMethod.type ?: PaymentMethodTypes.UNKNOWN
    }

    override fun createComponentState(outputData: CardOutputData) {
        Logger.v(TAG, "createComponentState")

        val cardNumber = outputData.cardNumberState.value

        val firstCardType = outputData.detectedCardTypes.firstOrNull()?.cardType

        val binValue = cardNumber.take(BIN_VALUE_LENGTH)

        val publicKey = publicKey

        // If data is not valid we just return empty object, encryption would fail and we don't pass unencrypted data.
        if (!outputData.isValid || publicKey == null) {
            _componentStateFlow.tryEmit(
                CardComponentState(
                    paymentComponentData = PaymentComponentData(),
                    isInputValid = outputData.isValid,
                    isReady = publicKey != null,
                    cardType = firstCardType,
                    binValue = binValue,
                    lastFourDigits = null
                )
            )
            return
        }

        val unencryptedCardBuilder = UnencryptedCard.Builder()

        val encryptedCard: EncryptedCard = try {
            unencryptedCardBuilder.setNumber(outputData.cardNumberState.value)
            if (!isCvcHidden()) {
                val cvc = outputData.securityCodeState.value
                if (cvc.isNotEmpty()) unencryptedCardBuilder.setCvc(cvc)
            }
            val expiryDateResult = outputData.expiryDateState.value
            if (expiryDateResult != ExpiryDate.EMPTY_DATE) {
                unencryptedCardBuilder.setExpiryMonth(expiryDateResult.expiryMonth.toString())
                unencryptedCardBuilder.setExpiryYear(expiryDateResult.expiryYear.toString())
            }

            cardEncrypter.encryptFields(unencryptedCardBuilder.build(), publicKey)
        } catch (e: EncryptionException) {
            _exceptionFlow.tryEmit(e)
            _componentStateFlow.tryEmit(
                CardComponentState(
                    paymentComponentData = PaymentComponentData(),
                    isInputValid = false,
                    isReady = true,
                    cardType = firstCardType,
                    binValue = binValue,
                    lastFourDigits = null
                )
            )
            return
        }

        _componentStateFlow.tryEmit(
            mapComponentState(
                encryptedCard,
                outputData,
                cardNumber,
                firstCardType,
                binValue
            )
        )
    }

    // Validation
    private fun validateCardNumber(
        cardNumber: String,
        enableLuhnCheck: Boolean,
        isBrandSupported: Boolean
    ): FieldState<String> {
        val validation = CardValidationUtils.validateCardNumber(cardNumber, enableLuhnCheck, isBrandSupported)
        return cardValidationMapper.mapCardNumberValidation(cardNumber, validation)
    }

    private fun validateExpiryDate(
        expiryDate: ExpiryDate,
        expiryDatePolicy: Brand.FieldPolicy?
    ): FieldState<ExpiryDate> {
        return CardValidationUtils.validateExpiryDate(expiryDate, expiryDatePolicy)
    }

    private fun validateSecurityCode(
        securityCode: String,
        cardType: DetectedCardType?
    ): FieldState<String> {
        return if (configuration.isHideCvc) {
            FieldState(
                securityCode,
                Validation.Valid
            )
        } else {
            CardValidationUtils.validateSecurityCode(securityCode, cardType)
        }
    }

    private fun validateHolderName(holderName: String): FieldState<String> {
        return if (configuration.isHolderNameRequired && holderName.isBlank()) {
            FieldState(
                holderName,
                Validation.Invalid(R.string.checkout_holder_name_not_valid)
            )
        } else {
            FieldState(
                holderName,
                Validation.Valid
            )
        }
    }

    private fun validateSocialSecurityNumber(socialSecurityNumber: String): FieldState<String> {
        return if (isSocialSecurityNumberRequired()) {
            SocialSecurityNumberUtils.validateSocialSecurityNumber(socialSecurityNumber)
        } else {
            FieldState(socialSecurityNumber, Validation.Valid)
        }
    }

    private fun validateKcpBirthDateOrTaxNumber(kcpBirthDateOrTaxNumber: String): FieldState<String> {
        return if (isKCPAuthRequired()) {
            KcpValidationUtils.validateKcpBirthDateOrTaxNumber(kcpBirthDateOrTaxNumber)
        } else {
            FieldState(kcpBirthDateOrTaxNumber, Validation.Valid)
        }
    }

    private fun validateKcpCardPassword(kcpCardPassword: String): FieldState<String> {
        return if (isKCPAuthRequired()) {
            KcpValidationUtils.validateKcpCardPassword(kcpCardPassword)
        } else {
            FieldState(kcpCardPassword, Validation.Valid)
        }
    }

    private fun validateAddress(
        addressInputModel: AddressInputModel,
        addressFormUIState: AddressFormUIState
    ): AddressOutputData {
        return AddressValidationUtils.validateAddressInput(addressInputModel, addressFormUIState)
    }

    //
    private fun isCvcHidden(): Boolean {
        return configuration.isHideCvc
    }

    private fun isSocialSecurityNumberRequired(): Boolean {
        return configuration.socialSecurityNumberVisibility == SocialSecurityNumberVisibility.SHOW
    }

    private fun isKCPAuthRequired(): Boolean {
        return configuration.kcpAuthVisibility == KCPAuthVisibility.SHOW
    }

    override fun requiresInput(): Boolean {
        return true
    }

    override fun isHolderNameRequired(): Boolean {
        return configuration.isHolderNameRequired
    }

    private fun isAddressRequired(addressFormUIState: AddressFormUIState): Boolean {
        return AddressFormUtils.isAddressRequired(addressFormUIState)
    }

    private fun getFundingSource(): String? {
        return paymentMethod.fundingSource
    }

    private fun getInstallmentOptions(
        installmentConfiguration: InstallmentConfiguration?,
        cardType: CardType?,
        isCardTypeReliable: Boolean
    ): List<InstallmentModel> {
        val isDebit = getFundingSource() == DEBIT_FUNDING_SOURCE
        return if (isDebit) {
            emptyList()
        } else {
            InstallmentUtils.makeInstallmentOptions(installmentConfiguration, cardType, isCardTypeReliable)
        }
    }

    private fun getAddressFormUIState(
        addressConfiguration: AddressConfiguration?,
        addressVisibility: AddressVisibility
    ): AddressFormUIState {
        return AddressFormUtils.getAddressFormUIState(
            addressConfiguration,
            addressVisibility
        )
    }

    private fun getSupportedCardTypes(): List<CardType> = configuration.supportedCardTypes

    private fun requestCountryList() {
        val coroutineScope = coroutineScope ?: return
        addressDelegate.getCountryList(configuration, coroutineScope)
    }

    private fun requestStateList(countryCode: String?) {
        val coroutineScope = coroutineScope ?: return
        addressDelegate.getStateList(configuration, countryCode, coroutineScope)
    }

    private fun makeCvcUIState(cvcPolicy: Brand.FieldPolicy?): InputFieldUIState {
        Logger.d(TAG, "makeCvcUIState: $cvcPolicy")
        return when {
            isCvcHidden() -> InputFieldUIState.HIDDEN
            // We treat CvcPolicy.HIDDEN as OPTIONAL for now to avoid hiding and showing the cvc field while the user
            // is typing the card number.
            cvcPolicy == Brand.FieldPolicy.OPTIONAL
                || cvcPolicy == Brand.FieldPolicy.HIDDEN -> InputFieldUIState.OPTIONAL
            else -> InputFieldUIState.REQUIRED
        }
    }

    private fun makeExpiryDateUIState(expiryDatePolicy: Brand.FieldPolicy?): InputFieldUIState {
        return when (expiryDatePolicy) {
            Brand.FieldPolicy.OPTIONAL, Brand.FieldPolicy.HIDDEN -> InputFieldUIState.OPTIONAL
            else -> InputFieldUIState.REQUIRED
        }
    }

    private fun markSelectedCard(cards: List<DetectedCardType>, selectedIndex: Int): List<DetectedCardType> {
        if (cards.size <= SINGLE_CARD_LIST_SIZE) return cards
        return cards.mapIndexed { index, card ->
            if (index == selectedIndex) {
                card.copy(isSelected = true)
            } else {
                card
            }
        }
    }

    private fun makeInstallmentFieldState(installmentModel: InstallmentModel?): FieldState<InstallmentModel?> {
        return FieldState(installmentModel, Validation.Valid)
    }

    private fun mapComponentState(
        encryptedCard: EncryptedCard,
        stateOutputData: CardOutputData,
        cardNumber: String,
        firstCardType: CardType?,
        binValue: String
    ): CardComponentState {
        val cardPaymentMethod = CardPaymentMethod()
        cardPaymentMethod.type = CardPaymentMethod.PAYMENT_METHOD_TYPE

        cardPaymentMethod.encryptedCardNumber = encryptedCard.encryptedCardNumber
        cardPaymentMethod.encryptedExpiryMonth = encryptedCard.encryptedExpiryMonth
        cardPaymentMethod.encryptedExpiryYear = encryptedCard.encryptedExpiryYear

        if (!isCvcHidden()) {
            cardPaymentMethod.encryptedSecurityCode = encryptedCard.encryptedSecurityCode
        }

        if (isHolderNameRequired()) {
            cardPaymentMethod.holderName = stateOutputData.holderNameState.value
        }

        if (isKCPAuthRequired()) {
            publicKey?.let { publicKey ->
                cardPaymentMethod.encryptedPassword = GenericEncrypter.encryptField(
                    GenericEncrypter.KCP_PASSWORD_KEY,
                    stateOutputData.kcpCardPasswordState.value,
                    publicKey
                )
            } ?: throw CheckoutException("Encryption failed because public key cannot be found.")
            cardPaymentMethod.taxNumber = stateOutputData.kcpBirthDateOrTaxNumberState.value
        }

        if (isDualBrandedFlow(stateOutputData)) {
            cardPaymentMethod.brand = stateOutputData.detectedCardTypes.first { it.isSelected }.cardType.txVariant
        }

        cardPaymentMethod.fundingSource = getFundingSource()

        try {
            cardPaymentMethod.threeDS2SdkVersion = ThreeDS2Service.INSTANCE.sdkVersion
        } catch (e: ClassNotFoundException) {
            Logger.e(TAG, "threeDS2SdkVersion not set because 3DS2 SDK is not present in project.")
        } catch (e: NoClassDefFoundError) {
            Logger.e(TAG, "threeDS2SdkVersion not set because 3DS2 SDK is not present in project.")
        }

        val paymentComponentData = makePaymentComponentData(cardPaymentMethod, stateOutputData)

        val lastFour = cardNumber.takeLast(LAST_FOUR_LENGTH)

        return CardComponentState(
            paymentComponentData = paymentComponentData,
            isInputValid = true,
            isReady = true,
            cardType = firstCardType,
            binValue = binValue,
            lastFourDigits = lastFour
        )
    }

    override fun isDualBrandedFlow(cardOutputData: CardOutputData): Boolean {
        val reliableDetectedCards = cardOutputData.detectedCardTypes.filter { it.isReliable }
        return reliableDetectedCards.size > 1 && reliableDetectedCards.any { it.isSelected }
    }

    override fun showStorePaymentField(): Boolean {
        return configuration.isStorePaymentFieldVisible
    }

    override fun getKcpBirthDateOrTaxNumberHint(input: String): Int {
        return when {
            input.length > KcpValidationUtils.KCP_BIRTH_DATE_LENGTH -> R.string.checkout_kcp_tax_number_hint
            else -> R.string.checkout_kcp_birth_date_or_tax_number_hint
        }
    }

    private fun makePaymentComponentData(
        cardPaymentMethod: CardPaymentMethod,
        stateOutputData: CardOutputData
    ): PaymentComponentData<CardPaymentMethod> {
        return PaymentComponentData<CardPaymentMethod>().apply {
            paymentMethod = cardPaymentMethod
            storePaymentMethod = stateOutputData.isStoredPaymentMethodEnable
            shopperReference = configuration.shopperReference
            if (isSocialSecurityNumberRequired()) {
                socialSecurityNumber = stateOutputData.socialSecurityNumberState.value
            }
            if (isAddressRequired(stateOutputData.addressUIState)) {
                billingAddress = AddressFormUtils.makeAddressData(
                    addressOutputData = stateOutputData.addressState,
                    addressFormUIState = stateOutputData.addressUIState
                )
            }
            if (isInstallmentsRequired(stateOutputData)) {
                installments = InstallmentUtils.makeInstallmentModelObject(stateOutputData.installmentState.value)
            }
        }
    }

    override fun isInstallmentsRequired(cardOutputData: CardOutputData): Boolean {
        return cardOutputData.installmentOptions.isNotEmpty()
    }

    override fun clear() {
        this.coroutineScope = null
    }

    companion object {
        private val TAG = LogUtil.getTag()
        private const val DEBIT_FUNDING_SOURCE = "debit"
        private const val BIN_VALUE_LENGTH = 6
        private const val LAST_FOUR_LENGTH = 4
        private const val SINGLE_CARD_LIST_SIZE = 1
    }
}
