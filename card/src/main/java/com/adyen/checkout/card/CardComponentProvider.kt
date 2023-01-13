/*
 * Copyright (c) 2019 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by caiof on 23/7/2019.
 */
package com.adyen.checkout.card

import android.app.Application
import android.os.Bundle
import androidx.annotation.RestrictTo
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.adyen.checkout.action.DefaultActionHandlingComponent
import com.adyen.checkout.action.GenericActionComponentProvider
import com.adyen.checkout.card.api.BinLookupService
import com.adyen.checkout.card.repository.DefaultDetectCardTypeRepository
import com.adyen.checkout.components.ActionComponentData
import com.adyen.checkout.components.ComponentError
import com.adyen.checkout.components.StoredPaymentComponentProvider
import com.adyen.checkout.components.analytics.AnalyticsMapper
import com.adyen.checkout.components.analytics.AnalyticsSource
import com.adyen.checkout.components.analytics.DefaultAnalyticsRepository
import com.adyen.checkout.components.api.AddressService
import com.adyen.checkout.components.api.AnalyticsService
import com.adyen.checkout.components.api.PublicKeyService
import com.adyen.checkout.components.base.ComponentCallback
import com.adyen.checkout.components.base.ComponentParams
import com.adyen.checkout.components.base.DefaultComponentEventHandler
import com.adyen.checkout.components.base.lifecycle.get
import com.adyen.checkout.components.base.lifecycle.viewModelFactory
import com.adyen.checkout.components.model.paymentmethods.PaymentMethod
import com.adyen.checkout.components.model.paymentmethods.StoredPaymentMethod
import com.adyen.checkout.components.repository.DefaultAddressRepository
import com.adyen.checkout.components.repository.DefaultPublicKeyRepository
import com.adyen.checkout.components.repository.PaymentObserverRepository
import com.adyen.checkout.components.ui.SubmitHandler
import com.adyen.checkout.core.api.HttpClientFactory
import com.adyen.checkout.core.exception.ComponentException
import com.adyen.checkout.cse.DefaultCardEncrypter
import com.adyen.checkout.cse.DefaultGenericEncrypter
import com.adyen.checkout.sessions.CheckoutSession
import com.adyen.checkout.sessions.SessionComponentCallback
import com.adyen.checkout.sessions.SessionHandler
import com.adyen.checkout.sessions.SessionSavedStateHandleContainer
import com.adyen.checkout.sessions.api.SessionService
import com.adyen.checkout.sessions.interactor.SessionInteractor
import com.adyen.checkout.sessions.repository.SessionRepository

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class CardComponentProvider(
    overrideComponentParams: ComponentParams? = null,
) : StoredPaymentComponentProvider<CardComponent, CardConfiguration> {

    private val componentParamsMapper = CardComponentParamsMapper(overrideComponentParams)

    override fun get(
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        paymentMethod: PaymentMethod,
        configuration: CardConfiguration,
        application: Application,
        defaultArgs: Bundle?,
        key: String?
    ): CardComponent {
        assertSupported(paymentMethod)

        val factory = viewModelFactory(savedStateRegistryOwner, defaultArgs) { savedStateHandle ->
            val componentParams = componentParamsMapper.mapToParamsDefault(configuration, paymentMethod)
            val httpClient = HttpClientFactory.getHttpClient(componentParams.environment)
            val genericEncrypter = DefaultGenericEncrypter()
            val cardEncrypter = DefaultCardEncrypter(genericEncrypter)
            val binLookupService = BinLookupService(httpClient)
            val detectCardTypeRepository = DefaultDetectCardTypeRepository(cardEncrypter, binLookupService)
            val publicKeyService = PublicKeyService(httpClient)
            val publicKeyRepository = DefaultPublicKeyRepository(publicKeyService)
            val addressService = AddressService(httpClient)
            val addressRepository = DefaultAddressRepository(addressService)
            val cardValidationMapper = CardValidationMapper()
            val analyticsService = AnalyticsService(httpClient)
            val analyticsRepository = DefaultAnalyticsRepository(
                packageName = application.packageName,
                locale = componentParams.shopperLocale,
                source = AnalyticsSource.PaymentComponent(componentParams.isCreatedByDropIn, paymentMethod),
                analyticsService = analyticsService,
                analyticsMapper = AnalyticsMapper(),
            )

            val cardDelegate = DefaultCardDelegate(
                observerRepository = PaymentObserverRepository(),
                publicKeyRepository = publicKeyRepository,
                componentParams = componentParams,
                paymentMethod = paymentMethod,
                analyticsRepository = analyticsRepository,
                addressRepository = addressRepository,
                detectCardTypeRepository = detectCardTypeRepository,
                cardValidationMapper = cardValidationMapper,
                cardEncrypter = cardEncrypter,
                genericEncrypter = genericEncrypter,
                submitHandler = SubmitHandler()
            )

            val genericActionDelegate = GenericActionComponentProvider(componentParams).getDelegate(
                configuration = configuration.genericActionConfiguration,
                savedStateHandle = savedStateHandle,
                application = application,
            )

            val componentEventHandler = DefaultComponentEventHandler(object : ComponentCallback<CardComponentState> {
                // TODO sessions: remove
                override fun onSubmit(state: CardComponentState) = Unit

                // TODO sessions: remove
                override fun onAdditionalDetails(actionComponentData: ActionComponentData) = Unit

                // TODO sessions: remove
                override fun onError(componentError: ComponentError) = Unit
            })

            CardComponent(
                cardDelegate = cardDelegate,
                genericActionDelegate = genericActionDelegate,
                actionHandlingComponent = DefaultActionHandlingComponent(genericActionDelegate, cardDelegate),
                componentEventHandler = componentEventHandler,
            )
        }
        return ViewModelProvider(viewModelStoreOwner, factory)[key, CardComponent::class.java]
    }

    @Suppress("LongParameterList", "LongMethod")
    fun get(
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        lifecycleOwner: LifecycleOwner,
        paymentMethod: PaymentMethod,
        configuration: CardConfiguration,
        application: Application,
        defaultArgs: Bundle?,
        key: String?,
        componentCallback: ComponentCallback<CardComponentState>,
    ): CardComponent {
        assertSupported(paymentMethod)

        val factory = viewModelFactory(savedStateRegistryOwner, defaultArgs) { savedStateHandle ->
            val componentParams = componentParamsMapper.mapToParamsDefault(configuration, paymentMethod)
            val httpClient = HttpClientFactory.getHttpClient(componentParams.environment)
            val genericEncrypter = DefaultGenericEncrypter()
            val cardEncrypter = DefaultCardEncrypter(genericEncrypter)
            val binLookupService = BinLookupService(httpClient)
            val detectCardTypeRepository = DefaultDetectCardTypeRepository(cardEncrypter, binLookupService)
            val publicKeyService = PublicKeyService(httpClient)
            val publicKeyRepository = DefaultPublicKeyRepository(publicKeyService)
            val addressService = AddressService(httpClient)
            val addressRepository = DefaultAddressRepository(addressService)
            val cardValidationMapper = CardValidationMapper()
            val analyticsService = AnalyticsService(httpClient)
            val analyticsRepository = DefaultAnalyticsRepository(
                packageName = application.packageName,
                locale = componentParams.shopperLocale,
                source = AnalyticsSource.PaymentComponent(componentParams.isCreatedByDropIn, paymentMethod),
                analyticsService = analyticsService,
                analyticsMapper = AnalyticsMapper(),
            )

            val cardDelegate = DefaultCardDelegate(
                observerRepository = PaymentObserverRepository(),
                publicKeyRepository = publicKeyRepository,
                componentParams = componentParams,
                paymentMethod = paymentMethod,
                analyticsRepository = analyticsRepository,
                addressRepository = addressRepository,
                detectCardTypeRepository = detectCardTypeRepository,
                cardValidationMapper = cardValidationMapper,
                cardEncrypter = cardEncrypter,
                genericEncrypter = genericEncrypter,
                submitHandler = SubmitHandler()
            )

            val genericActionDelegate = GenericActionComponentProvider(componentParams).getDelegate(
                configuration = configuration.genericActionConfiguration,
                savedStateHandle = savedStateHandle,
                application = application,
            )

            val componentEventHandler = DefaultComponentEventHandler(componentCallback)

            CardComponent(
                cardDelegate = cardDelegate,
                genericActionDelegate = genericActionDelegate,
                actionHandlingComponent = DefaultActionHandlingComponent(genericActionDelegate, cardDelegate),
                componentEventHandler = componentEventHandler,
            )
        }
        return ViewModelProvider(viewModelStoreOwner, factory)[key, CardComponent::class.java].also { component ->
            component.observe(lifecycleOwner, component.componentEventHandler::onPaymentComponentEvent)
        }
    }

    @Suppress("LongParameterList", "LongMethod")
    fun get(
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        lifecycleOwner: LifecycleOwner,
        checkoutSession: CheckoutSession,
        paymentMethod: PaymentMethod,
        configuration: CardConfiguration,
        application: Application,
        defaultArgs: Bundle?,
        key: String?,
        componentCallback: SessionComponentCallback<CardComponentState>
    ): CardComponent {
        assertSupported(paymentMethod)

        val factory = viewModelFactory(savedStateRegistryOwner, defaultArgs) { savedStateHandle ->
            val componentParams = componentParamsMapper.mapToParamsDefault(configuration, paymentMethod)
            val httpClient = HttpClientFactory.getHttpClient(componentParams.environment)
            val genericEncrypter = DefaultGenericEncrypter()
            val cardEncrypter = DefaultCardEncrypter(genericEncrypter)
            val binLookupService = BinLookupService(httpClient)
            val detectCardTypeRepository = DefaultDetectCardTypeRepository(cardEncrypter, binLookupService)
            val publicKeyService = PublicKeyService(httpClient)
            val publicKeyRepository = DefaultPublicKeyRepository(publicKeyService)
            val addressService = AddressService(httpClient)
            val addressRepository = DefaultAddressRepository(addressService)
            val cardValidationMapper = CardValidationMapper()
            val analyticsService = AnalyticsService(httpClient)
            val analyticsRepository = DefaultAnalyticsRepository(
                packageName = application.packageName,
                locale = componentParams.shopperLocale,
                source = AnalyticsSource.PaymentComponent(componentParams.isCreatedByDropIn, paymentMethod),
                analyticsService = analyticsService,
                analyticsMapper = AnalyticsMapper(),
            )

            val cardDelegate = DefaultCardDelegate(
                observerRepository = PaymentObserverRepository(),
                publicKeyRepository = publicKeyRepository,
                componentParams = componentParams,
                paymentMethod = paymentMethod,
                analyticsRepository = analyticsRepository,
                addressRepository = addressRepository,
                detectCardTypeRepository = detectCardTypeRepository,
                cardValidationMapper = cardValidationMapper,
                cardEncrypter = cardEncrypter,
                genericEncrypter = genericEncrypter,
                submitHandler = SubmitHandler()
            )

            val genericActionDelegate = GenericActionComponentProvider(componentParams).getDelegate(
                configuration = configuration.genericActionConfiguration,
                savedStateHandle = savedStateHandle,
                application = application,
            )

            val sessionSavedStateHandleContainer = SessionSavedStateHandleContainer(
                savedStateHandle = savedStateHandle,
                checkoutSession = checkoutSession,
            )
            val sessionInteractor = SessionInteractor(
                sessionRepository = SessionRepository(
                    sessionService = SessionService(httpClient),
                    clientKey = componentParams.clientKey,
                ),
                sessionModel = sessionSavedStateHandleContainer.getSessionModel(),
                isFlowTakenOver = sessionSavedStateHandleContainer.isFlowTakenOver ?: false
            )
            val sessionHandler = SessionHandler(
                sessionInteractor = sessionInteractor,
                sessionSavedStateHandleContainer = sessionSavedStateHandleContainer,
                sessionComponentCallback = componentCallback
            )

            CardComponent(
                cardDelegate = cardDelegate,
                genericActionDelegate = genericActionDelegate,
                actionHandlingComponent = DefaultActionHandlingComponent(genericActionDelegate, cardDelegate),
                componentEventHandler = sessionHandler,
            )
        }

        return ViewModelProvider(viewModelStoreOwner, factory)[key, CardComponent::class.java].also { component ->
            component.observe(lifecycleOwner, component.componentEventHandler::onPaymentComponentEvent)
        }
    }

    override fun get(
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        storedPaymentMethod: StoredPaymentMethod,
        configuration: CardConfiguration,
        application: Application,
        defaultArgs: Bundle?,
        key: String?,
    ): CardComponent {
        assertSupported(storedPaymentMethod)

        val factory = viewModelFactory(savedStateRegistryOwner, defaultArgs) { savedStateHandle ->
            val componentParams = componentParamsMapper.mapToParamsStored(configuration)
            val httpClient = HttpClientFactory.getHttpClient(componentParams.environment)
            val publicKeyService = PublicKeyService(httpClient)
            val publicKeyRepository = DefaultPublicKeyRepository(publicKeyService)
            val genericEncrypter = DefaultGenericEncrypter()
            val cardEncrypter = DefaultCardEncrypter(genericEncrypter)

            val analyticsService = AnalyticsService(httpClient)
            val analyticsRepository = DefaultAnalyticsRepository(
                packageName = application.packageName,
                locale = componentParams.shopperLocale,
                source = AnalyticsSource.PaymentComponent(componentParams.isCreatedByDropIn, storedPaymentMethod),
                analyticsService = analyticsService,
                analyticsMapper = AnalyticsMapper(),
            )

            val cardDelegate = StoredCardDelegate(
                observerRepository = PaymentObserverRepository(),
                storedPaymentMethod = storedPaymentMethod,
                componentParams = componentParams,
                analyticsRepository = analyticsRepository,
                cardEncrypter = cardEncrypter,
                publicKeyRepository = publicKeyRepository,
                submitHandler = SubmitHandler()
            )

            val genericActionDelegate = GenericActionComponentProvider(componentParams).getDelegate(
                configuration = configuration.genericActionConfiguration,
                savedStateHandle = savedStateHandle,
                application = application,
            )

            val componentEventHandler = DefaultComponentEventHandler(object : ComponentCallback<CardComponentState> {
                // TODO sessions: remove
                override fun onSubmit(state: CardComponentState) = Unit

                // TODO sessions: remove
                override fun onAdditionalDetails(actionComponentData: ActionComponentData) = Unit

                // TODO sessions: remove
                override fun onError(componentError: ComponentError) = Unit
            })

            CardComponent(
                cardDelegate = cardDelegate,
                genericActionDelegate = genericActionDelegate,
                actionHandlingComponent = DefaultActionHandlingComponent(genericActionDelegate, cardDelegate),
                componentEventHandler = componentEventHandler,
            )
        }
        return ViewModelProvider(viewModelStoreOwner, factory)[key, CardComponent::class.java]
    }

    private fun assertSupported(paymentMethod: PaymentMethod) {
        if (!isPaymentMethodSupported(paymentMethod)) {
            throw ComponentException("Unsupported payment method ${paymentMethod.type}")
        }
    }

    private fun assertSupported(storedPaymentMethod: StoredPaymentMethod) {
        if (!isPaymentMethodSupported(storedPaymentMethod)) {
            throw ComponentException("Unsupported payment method ${storedPaymentMethod.type}")
        }
    }

    override fun isPaymentMethodSupported(paymentMethod: PaymentMethod): Boolean {
        return CardComponent.PAYMENT_METHOD_TYPES.contains(paymentMethod.type)
    }

    override fun isPaymentMethodSupported(storedPaymentMethod: StoredPaymentMethod): Boolean {
        return CardComponent.PAYMENT_METHOD_TYPES.contains(storedPaymentMethod.type)
    }
}
