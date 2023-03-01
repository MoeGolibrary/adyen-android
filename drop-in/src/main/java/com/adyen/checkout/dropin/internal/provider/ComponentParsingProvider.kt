/*
 * Copyright (c) 2019 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by caiof on 24/4/2019.
 */

@file:Suppress("TooManyFunctions")

package com.adyen.checkout.dropin.internal.provider

import android.app.Application
import androidx.fragment.app.Fragment
import com.adyen.checkout.ach.ACHDirectDebitComponent
import com.adyen.checkout.ach.ACHDirectDebitComponentState
import com.adyen.checkout.ach.ACHDirectDebitConfiguration
import com.adyen.checkout.ach.internal.provider.ACHDirectDebitComponentProvider
import com.adyen.checkout.bacs.BacsDirectDebitComponent
import com.adyen.checkout.bacs.BacsDirectDebitComponentState
import com.adyen.checkout.bacs.BacsDirectDebitConfiguration
import com.adyen.checkout.bacs.internal.provider.BacsDirectDebitComponentProvider
import com.adyen.checkout.bcmc.BcmcComponent
import com.adyen.checkout.bcmc.BcmcComponentState
import com.adyen.checkout.bcmc.BcmcConfiguration
import com.adyen.checkout.bcmc.internal.provider.BcmcComponentProvider
import com.adyen.checkout.blik.BlikComponent
import com.adyen.checkout.blik.BlikComponentState
import com.adyen.checkout.blik.BlikConfiguration
import com.adyen.checkout.blik.internal.provider.BlikComponentProvider
import com.adyen.checkout.card.CardComponent
import com.adyen.checkout.card.CardComponentState
import com.adyen.checkout.card.CardConfiguration
import com.adyen.checkout.card.internal.provider.CardComponentProvider
import com.adyen.checkout.components.core.Amount
import com.adyen.checkout.components.core.PaymentMethod
import com.adyen.checkout.components.core.StoredPaymentMethod
import com.adyen.checkout.components.core.internal.AlwaysAvailablePaymentMethod
import com.adyen.checkout.components.core.internal.BaseConfigurationBuilder
import com.adyen.checkout.components.core.internal.ComponentAvailableCallback
import com.adyen.checkout.components.core.internal.ComponentCallback
import com.adyen.checkout.components.core.internal.Configuration
import com.adyen.checkout.components.core.internal.PaymentComponent
import com.adyen.checkout.components.core.internal.PaymentMethodAvailabilityCheck
import com.adyen.checkout.components.core.internal.provider.PaymentComponentProvider
import com.adyen.checkout.components.core.internal.util.PaymentMethodTypes
import com.adyen.checkout.conveniencestoresjp.ConvenienceStoresJPComponent
import com.adyen.checkout.conveniencestoresjp.ConvenienceStoresJPComponentState
import com.adyen.checkout.conveniencestoresjp.ConvenienceStoresJPConfiguration
import com.adyen.checkout.conveniencestoresjp.internal.provider.ConvenienceStoresJPComponentProvider
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.internal.util.LogUtil
import com.adyen.checkout.core.internal.util.Logger
import com.adyen.checkout.dotpay.DotpayComponent
import com.adyen.checkout.dotpay.DotpayComponentState
import com.adyen.checkout.dotpay.DotpayConfiguration
import com.adyen.checkout.dotpay.internal.provider.DotpayComponentProvider
import com.adyen.checkout.dropin.DropInConfiguration
import com.adyen.checkout.dropin.internal.ui.model.DropInComponentParams
import com.adyen.checkout.dropin.internal.ui.model.DropInComponentParamsMapper
import com.adyen.checkout.entercash.EntercashComponent
import com.adyen.checkout.entercash.EntercashComponentState
import com.adyen.checkout.entercash.EntercashConfiguration
import com.adyen.checkout.entercash.internal.provider.EntercashComponentProvider
import com.adyen.checkout.eps.EPSComponent
import com.adyen.checkout.eps.EPSComponentState
import com.adyen.checkout.eps.EPSConfiguration
import com.adyen.checkout.eps.internal.provider.EPSComponentProvider
import com.adyen.checkout.giftcard.GiftCardComponent
import com.adyen.checkout.giftcard.GiftCardComponentState
import com.adyen.checkout.giftcard.GiftCardConfiguration
import com.adyen.checkout.giftcard.internal.provider.GiftCardComponentProvider
import com.adyen.checkout.googlepay.GooglePayComponent
import com.adyen.checkout.googlepay.GooglePayComponentState
import com.adyen.checkout.googlepay.GooglePayConfiguration
import com.adyen.checkout.googlepay.internal.provider.GooglePayComponentProvider
import com.adyen.checkout.ideal.IdealComponent
import com.adyen.checkout.ideal.IdealComponentState
import com.adyen.checkout.ideal.IdealConfiguration
import com.adyen.checkout.ideal.internal.provider.IdealComponentProvider
import com.adyen.checkout.instant.InstantComponentState
import com.adyen.checkout.instant.InstantPaymentComponent
import com.adyen.checkout.instant.InstantPaymentConfiguration
import com.adyen.checkout.instant.internal.provider.InstantPaymentComponentProvider
import com.adyen.checkout.mbway.MBWayComponent
import com.adyen.checkout.mbway.MBWayComponentState
import com.adyen.checkout.mbway.MBWayConfiguration
import com.adyen.checkout.mbway.internal.provider.MBWayComponentProvider
import com.adyen.checkout.molpay.MolpayComponent
import com.adyen.checkout.molpay.MolpayComponentState
import com.adyen.checkout.molpay.MolpayConfiguration
import com.adyen.checkout.molpay.internal.provider.MolpayComponentProvider
import com.adyen.checkout.onlinebankingcz.OnlineBankingCZComponent
import com.adyen.checkout.onlinebankingcz.OnlineBankingCZComponentState
import com.adyen.checkout.onlinebankingcz.OnlineBankingCZConfiguration
import com.adyen.checkout.onlinebankingcz.internal.provider.OnlineBankingCZComponentProvider
import com.adyen.checkout.onlinebankingjp.OnlineBankingJPComponent
import com.adyen.checkout.onlinebankingjp.OnlineBankingJPComponentState
import com.adyen.checkout.onlinebankingjp.OnlineBankingJPConfiguration
import com.adyen.checkout.onlinebankingjp.internal.provider.OnlineBankingJPComponentProvider
import com.adyen.checkout.onlinebankingpl.OnlineBankingPLComponent
import com.adyen.checkout.onlinebankingpl.OnlineBankingPLComponentState
import com.adyen.checkout.onlinebankingpl.OnlineBankingPLConfiguration
import com.adyen.checkout.onlinebankingpl.internal.provider.OnlineBankingPLComponentProvider
import com.adyen.checkout.onlinebankingsk.OnlineBankingSKComponent
import com.adyen.checkout.onlinebankingsk.OnlineBankingSKComponentState
import com.adyen.checkout.onlinebankingsk.OnlineBankingSKConfiguration
import com.adyen.checkout.onlinebankingsk.internal.provider.OnlineBankingSKComponentProvider
import com.adyen.checkout.openbanking.OpenBankingComponent
import com.adyen.checkout.openbanking.OpenBankingComponentState
import com.adyen.checkout.openbanking.OpenBankingConfiguration
import com.adyen.checkout.openbanking.internal.provider.OpenBankingComponentProvider
import com.adyen.checkout.paybybank.PayByBankComponent
import com.adyen.checkout.paybybank.PayByBankComponentState
import com.adyen.checkout.paybybank.PayByBankConfiguration
import com.adyen.checkout.paybybank.internal.provider.PayByBankComponentProvider
import com.adyen.checkout.payeasy.PayEasyComponent
import com.adyen.checkout.payeasy.PayEasyComponentState
import com.adyen.checkout.payeasy.PayEasyConfiguration
import com.adyen.checkout.payeasy.internal.provider.PayEasyComponentProvider
import com.adyen.checkout.sepa.SepaComponent
import com.adyen.checkout.sepa.SepaComponentState
import com.adyen.checkout.sepa.SepaConfiguration
import com.adyen.checkout.sepa.internal.provider.SepaComponentProvider
import com.adyen.checkout.sessions.core.SessionSetupConfiguration
import com.adyen.checkout.seveneleven.SevenElevenComponent
import com.adyen.checkout.seveneleven.SevenElevenComponentState
import com.adyen.checkout.seveneleven.SevenElevenConfiguration
import com.adyen.checkout.seveneleven.internal.provider.SevenElevenComponentProvider
import com.adyen.checkout.wechatpay.internal.WeChatPayProvider

private val TAG = LogUtil.getTag()

internal inline fun <reified T : Configuration> getConfigurationForPaymentMethod(
    paymentMethod: PaymentMethod,
    dropInConfiguration: DropInConfiguration,
): T {
    val paymentMethodType = paymentMethod.type ?: throw CheckoutException("Payment method type is null")
    return dropInConfiguration.getConfigurationForPaymentMethod(paymentMethodType) ?: getDefaultConfigForPaymentMethod(
        paymentMethod,
        dropInConfiguration
    )
}

internal inline fun <reified T : Configuration> getConfigurationForPaymentMethod(
    storedPaymentMethod: StoredPaymentMethod,
    dropInConfiguration: DropInConfiguration,
): T {
    val storedPaymentMethodType = storedPaymentMethod.type ?: throw CheckoutException("Payment method type is null")
    return dropInConfiguration.getConfigurationForPaymentMethod(storedPaymentMethodType)
        ?: getDefaultConfigForPaymentMethod(
            storedPaymentMethod = storedPaymentMethod,
            dropInConfiguration = dropInConfiguration
        )
}

internal fun <T : Configuration> getDefaultConfigForPaymentMethod(
    storedPaymentMethod: StoredPaymentMethod,
    dropInConfiguration: DropInConfiguration
): T {
    val shopperLocale = dropInConfiguration.shopperLocale
    val environment = dropInConfiguration.environment
    val clientKey = dropInConfiguration.clientKey
    val builder: BaseConfigurationBuilder<*, *> = when {
        BlikComponent.PROVIDER.isPaymentMethodSupported(storedPaymentMethod) -> BlikConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        CardComponent.PROVIDER.isPaymentMethodSupported(storedPaymentMethod) -> CardConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        else -> throw CheckoutException(
            errorMessage = "Unable to find component configuration for storedPaymentMethod - $storedPaymentMethod"
        )
    }
    @Suppress("UNCHECKED_CAST")
    return builder.build() as T
}

@Suppress("LongMethod")
internal fun <T : Configuration> getDefaultConfigForPaymentMethod(
    paymentMethod: PaymentMethod,
    dropInConfiguration: DropInConfiguration
): T {
    val shopperLocale = dropInConfiguration.shopperLocale
    val environment = dropInConfiguration.environment
    val clientKey = dropInConfiguration.clientKey

    // get default builder for Configuration type
    val builder: BaseConfigurationBuilder<*, *> = when {
        ACHDirectDebitComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> ACHDirectDebitConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        BacsDirectDebitComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) ->
            BacsDirectDebitConfiguration.Builder(
                shopperLocale = shopperLocale,
                environment = environment,
                clientKey = clientKey
            )
        BcmcComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> BcmcConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        BlikComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> BlikConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        CardComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> CardConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        ConvenienceStoresJPComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) ->
            ConvenienceStoresJPConfiguration.Builder(
                shopperLocale = shopperLocale,
                environment = environment,
                clientKey = clientKey
            )
        DotpayComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> DotpayConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        EntercashComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> EntercashConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        EPSComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> EPSConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        GiftCardComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> GiftCardConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        GooglePayComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) ->
            GooglePayConfiguration.Builder(
                shopperLocale = shopperLocale,
                environment = environment,
                clientKey = clientKey
            )
        IdealComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> IdealConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        InstantPaymentComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> InstantPaymentConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        MBWayComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> MBWayConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        MolpayComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> MolpayConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        OnlineBankingCZComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) ->
            OnlineBankingCZConfiguration.Builder(
                shopperLocale = shopperLocale,
                environment = environment,
                clientKey = clientKey
            )
        OnlineBankingJPComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) ->
            OnlineBankingJPConfiguration.Builder(
                shopperLocale = shopperLocale,
                environment = environment,
                clientKey = clientKey
            )
        OnlineBankingPLComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) ->
            OnlineBankingPLConfiguration.Builder(
                shopperLocale = shopperLocale,
                environment = environment,
                clientKey = clientKey
            )
        OnlineBankingSKComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) ->
            OnlineBankingSKConfiguration.Builder(
                shopperLocale = shopperLocale,
                environment = environment,
                clientKey = clientKey
            )
        OpenBankingComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> OpenBankingConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        PayByBankComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> PayByBankConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        PayEasyComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> PayEasyConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        SepaComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> SepaConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        SevenElevenComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> SevenElevenConfiguration.Builder(
            shopperLocale = shopperLocale,
            environment = environment,
            clientKey = clientKey
        )
        else -> throw CheckoutException("Unable to find component configuration for paymentMethod - $paymentMethod")
    }

    @Suppress("UNCHECKED_CAST")
    return builder.build() as T
}

private inline fun <reified T : Configuration> getConfigurationForPaymentMethodOrNull(
    paymentMethod: PaymentMethod,
    dropInConfiguration: DropInConfiguration,
): T? {
    return try {
        getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
    } catch (e: CheckoutException) {
        null
    }
}

internal fun checkPaymentMethodAvailability(
    application: Application,
    paymentMethod: PaymentMethod,
    dropInConfiguration: DropInConfiguration,
    amount: Amount,
    callback: ComponentAvailableCallback
) {
    try {
        Logger.v(TAG, "Checking availability for type - ${paymentMethod.type}")

        val type = paymentMethod.type ?: throw CheckoutException("PaymentMethod type is null")

        val availabilityCheck = getPaymentMethodAvailabilityCheck(dropInConfiguration, type, amount)
        val configuration =
            getConfigurationForPaymentMethodOrNull<Configuration>(paymentMethod, dropInConfiguration)

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
    amount: Amount,
    sessionSetupConfiguration: SessionSetupConfiguration? = null,
): PaymentMethodAvailabilityCheck<Configuration> {
    val dropInParams = dropInConfiguration.mapToParams(amount)
    @Suppress("UNCHECKED_CAST")
    return when (paymentMethodType) {
        PaymentMethodTypes.GOOGLE_PAY,
        PaymentMethodTypes.GOOGLE_PAY_LEGACY -> GooglePayComponentProvider(dropInParams, sessionSetupConfiguration)
        PaymentMethodTypes.WECHAT_PAY_SDK -> WeChatPayProvider()
        else -> AlwaysAvailablePaymentMethod()
    } as PaymentMethodAvailabilityCheck<Configuration>
}

/**
 * Provides a [PaymentComponent] from a [PaymentComponentProvider] using the [StoredPaymentMethod] reference.
 *
 * @param fragment The Fragment which the PaymentComponent lifecycle will be bound to.
 * @param storedPaymentMethod The stored payment method to be parsed.
 * @throws CheckoutException In case a component cannot be created.
 */
@Suppress("UNCHECKED_CAST", "LongParameterList")
internal fun getComponentFor(
    fragment: Fragment,
    storedPaymentMethod: StoredPaymentMethod,
    dropInConfiguration: DropInConfiguration,
    amount: Amount,
    componentCallback: ComponentCallback<*>,
    sessionSetupConfiguration: SessionSetupConfiguration?,
): PaymentComponent {
    val dropInParams = dropInConfiguration.mapToParams(amount)
    return when {
        CardComponent.PROVIDER.isPaymentMethodSupported(storedPaymentMethod) -> {
            val cardConfig: CardConfiguration =
                getConfigurationForPaymentMethod(storedPaymentMethod, dropInConfiguration)
            CardComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                storedPaymentMethod = storedPaymentMethod,
                configuration = cardConfig,
                componentCallback = componentCallback as ComponentCallback<CardComponentState>,
            )
        }
        BlikComponent.PROVIDER.isPaymentMethodSupported(storedPaymentMethod) -> {
            val blikConfig: BlikConfiguration =
                getConfigurationForPaymentMethod(storedPaymentMethod, dropInConfiguration)
            BlikComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                storedPaymentMethod = storedPaymentMethod,
                configuration = blikConfig,
                componentCallback = componentCallback as ComponentCallback<BlikComponentState>,
            )
        }
        else -> {
            throw CheckoutException("Unable to find stored component for type - ${storedPaymentMethod.type}")
        }
    }
}

/**
 * Provides a [PaymentComponent] from a [PaymentComponentProvider] using the [PaymentMethod] reference.
 *
 * @param fragment The Fragment which the PaymentComponent lifecycle will be bound to.
 * @param paymentMethod The payment method to be parsed.
 * @throws CheckoutException In case a component cannot be created.
 */
@Suppress("LongMethod", "UNCHECKED_CAST", "LongParameterList")
internal fun getComponentFor(
    fragment: Fragment,
    paymentMethod: PaymentMethod,
    dropInConfiguration: DropInConfiguration,
    amount: Amount,
    componentCallback: ComponentCallback<*>,
    sessionSetupConfiguration: SessionSetupConfiguration?,
): PaymentComponent {
    val dropInParams = dropInConfiguration.mapToParams(amount)
    return when {
        ACHDirectDebitComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val configuration: ACHDirectDebitConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            ACHDirectDebitComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = configuration,
                componentCallback = componentCallback as ComponentCallback<ACHDirectDebitComponentState>,
            )
        }
        BacsDirectDebitComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val bacsConfiguration: BacsDirectDebitConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            BacsDirectDebitComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = bacsConfiguration,
                componentCallback = componentCallback as ComponentCallback<BacsDirectDebitComponentState>,
            )
        }
        BcmcComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val bcmcConfiguration: BcmcConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            BcmcComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = bcmcConfiguration,
                componentCallback = componentCallback as ComponentCallback<BcmcComponentState>,
            )
        }
        BlikComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val blikConfiguration: BlikConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            BlikComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = blikConfiguration,
                componentCallback = componentCallback as ComponentCallback<BlikComponentState>,
            )
        }
        CardComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val cardConfig: CardConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            CardComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = cardConfig,
                componentCallback = componentCallback as ComponentCallback<CardComponentState>,
            )
        }
        ConvenienceStoresJPComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val convenienceStoresJPConfiguration: ConvenienceStoresJPConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            ConvenienceStoresJPComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = convenienceStoresJPConfiguration,
                componentCallback = componentCallback as ComponentCallback<ConvenienceStoresJPComponentState>,
            )
        }
        DotpayComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val dotpayConfig: DotpayConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            DotpayComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = dotpayConfig,
                componentCallback = componentCallback as ComponentCallback<DotpayComponentState>,
            )
        }
        EntercashComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val entercashConfig: EntercashConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            EntercashComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = entercashConfig,
                componentCallback = componentCallback as ComponentCallback<EntercashComponentState>,
            )
        }
        EPSComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val epsConfig: EPSConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            EPSComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = epsConfig,
                componentCallback = componentCallback as ComponentCallback<EPSComponentState>,
            )
        }
        GiftCardComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val giftcardConfiguration: GiftCardConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            GiftCardComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = giftcardConfiguration,
                componentCallback = componentCallback as ComponentCallback<GiftCardComponentState>,
            )
        }
        GooglePayComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val googlePayConfiguration: GooglePayConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            GooglePayComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = googlePayConfiguration,
                componentCallback = componentCallback as ComponentCallback<GooglePayComponentState>,
            )
        }
        IdealComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val idealConfig: IdealConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            IdealComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = idealConfig,
                componentCallback = componentCallback as ComponentCallback<IdealComponentState>,
            )
        }
        InstantPaymentComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val instantPaymentConfiguration: InstantPaymentConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            InstantPaymentComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = instantPaymentConfiguration,
                componentCallback = componentCallback as ComponentCallback<InstantComponentState>,
            )
        }
        MBWayComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val mbWayConfiguration: MBWayConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            MBWayComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = mbWayConfiguration,
                componentCallback = componentCallback as ComponentCallback<MBWayComponentState>,
            )
        }
        MolpayComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val molpayConfig: MolpayConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            MolpayComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = molpayConfig,
                componentCallback = componentCallback as ComponentCallback<MolpayComponentState>,
            )
        }
        OnlineBankingCZComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val onlineBankingCZConfig: OnlineBankingCZConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            OnlineBankingCZComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = onlineBankingCZConfig,
                componentCallback = componentCallback as ComponentCallback<OnlineBankingCZComponentState>,
            )
        }
        OnlineBankingJPComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val onlineBankingJPConfig: OnlineBankingJPConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            OnlineBankingJPComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = onlineBankingJPConfig,
                componentCallback = componentCallback as ComponentCallback<OnlineBankingJPComponentState>,
            )
        }
        OnlineBankingPLComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val onlineBankingPLConfig: OnlineBankingPLConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            OnlineBankingPLComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = onlineBankingPLConfig,
                componentCallback = componentCallback as ComponentCallback<OnlineBankingPLComponentState>,
            )
        }
        OnlineBankingSKComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val onlineBankingSKConfig: OnlineBankingSKConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            OnlineBankingSKComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = onlineBankingSKConfig,
                componentCallback = componentCallback as ComponentCallback<OnlineBankingSKComponentState>,
            )
        }
        OpenBankingComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val openBankingConfig: OpenBankingConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            OpenBankingComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = openBankingConfig,
                componentCallback = componentCallback as ComponentCallback<OpenBankingComponentState>,
            )
        }
        PayByBankComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val payByBankConfig: PayByBankConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            PayByBankComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = payByBankConfig,
                componentCallback = componentCallback as ComponentCallback<PayByBankComponentState>,
            )
        }
        PayEasyComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val payEasyConfiguration: PayEasyConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            PayEasyComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = payEasyConfiguration,
                componentCallback = componentCallback as ComponentCallback<PayEasyComponentState>,
            )
        }
        SepaComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val sepaConfiguration: SepaConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            SepaComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = sepaConfiguration,
                componentCallback = componentCallback as ComponentCallback<SepaComponentState>,
            )
        }
        SevenElevenComponent.PROVIDER.isPaymentMethodSupported(paymentMethod) -> {
            val sevenElevenConfiguration: SevenElevenConfiguration =
                getConfigurationForPaymentMethod(paymentMethod, dropInConfiguration)
            SevenElevenComponentProvider(dropInParams, sessionSetupConfiguration).get(
                fragment = fragment,
                paymentMethod = paymentMethod,
                configuration = sevenElevenConfiguration,
                componentCallback = componentCallback as ComponentCallback<SevenElevenComponentState>,
            )
        }
        else -> {
            throw CheckoutException("Unable to find component for type - ${paymentMethod.type}")
        }
    }
}

internal fun DropInConfiguration.mapToParams(amount: Amount): DropInComponentParams {
    return DropInComponentParamsMapper().mapToParams(this, amount)
}