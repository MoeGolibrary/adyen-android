/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by ozgur on 31/1/2023.
 */

package com.adyen.checkout.onlinebankingcore

import android.content.Context
import androidx.annotation.RestrictTo
import com.adyen.checkout.action.ActionHandlingPaymentMethodConfigurationBuilder
import com.adyen.checkout.action.GenericActionConfiguration
import com.adyen.checkout.components.base.ButtonConfiguration
import com.adyen.checkout.components.base.ButtonConfigurationBuilder
import com.adyen.checkout.components.base.Configuration
import com.adyen.checkout.core.api.Environment
import java.util.Locale

abstract class OnlineBankingConfiguration : Configuration, ButtonConfiguration {

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    abstract val genericActionConfiguration: GenericActionConfiguration

    abstract class OnlineBankingConfigurationBuilder<
        OnlineBankingConfigurationT : OnlineBankingConfiguration,
        IssuerListBuilderT : OnlineBankingConfigurationBuilder<OnlineBankingConfigurationT, IssuerListBuilderT>
        > :
        ActionHandlingPaymentMethodConfigurationBuilder<OnlineBankingConfigurationT, IssuerListBuilderT>,
        ButtonConfigurationBuilder {

        protected open var isSubmitButtonVisible: Boolean? = null

        protected constructor(context: Context, environment: Environment, clientKey: String) : super(
            context,
            environment,
            clientKey
        )

        protected constructor(
            shopperLocale: Locale,
            environment: Environment,
            clientKey: String
        ) : super(shopperLocale, environment, clientKey)

        /**
         * Sets if submit button will be visible or not.
         *
         * Default is True.
         *
         * @param isSubmitButtonVisible Is submit button should be visible or not.
         */
        override fun setSubmitButtonVisible(isSubmitButtonVisible: Boolean): IssuerListBuilderT {
            this.isSubmitButtonVisible = isSubmitButtonVisible
            @Suppress("UNCHECKED_CAST")
            return this as IssuerListBuilderT
        }
    }
}