/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 27/3/2023.
 */

package com.adyen.checkout.adyen3ds2.internal.ui.model

import com.adyen.checkout.adyen3ds2.Adyen3DS2Configuration
import com.adyen.checkout.components.core.Amount
import com.adyen.checkout.components.core.internal.ui.model.GenericComponentParams
import com.adyen.checkout.core.Environment
import com.adyen.threeds2.customization.UiCustomization
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Locale

internal class Adyen3DS2ComponentParamsMapperTest {

    @Test
    fun `when parent configuration is null and custom 3ds2 configuration fields are null then all fields should match`() {
        val adyen3DS2Configuration = getAdyen3DS2ConfigurationBuilder()
            .build()

        val params = Adyen3DS2ComponentParamsMapper(null, null).mapToParams(adyen3DS2Configuration, null)

        val expected = getAdyen3DS2ComponentParams()

        Assertions.assertEquals(expected, params)
    }

    @Test
    fun `when parent configuration is null and custom 3ds2 configuration fields are set then all fields should match`() {
        val uiCustomization = UiCustomization()

        val adyen3DS2Configuration = getAdyen3DS2ConfigurationBuilder()
            .setUiCustomization(uiCustomization)
            .build()

        val params = Adyen3DS2ComponentParamsMapper(null, null).mapToParams(adyen3DS2Configuration, null)

        val expected = getAdyen3DS2ComponentParams(
            uiCustomization = uiCustomization
        )

        Assertions.assertEquals(expected, params)
    }

    @Test
    fun `when parent configuration is set then parent configuration fields should override 3ds2 configuration fields`() {
        val adyen3DS2Configuration = getAdyen3DS2ConfigurationBuilder()
            .build()

        // this is in practice DropInComponentParams, but we don't have access to it in this module and any
        // ComponentParams class can work
        val overrideParams = GenericComponentParams(
            shopperLocale = Locale.GERMAN,
            environment = Environment.EUROPE,
            clientKey = TEST_CLIENT_KEY_2,
            isAnalyticsEnabled = false,
            isCreatedByDropIn = true,
            amount = Amount(
                currency = "USD",
                value = 25_00L
            )
        )

        val params = Adyen3DS2ComponentParamsMapper(overrideParams, null).mapToParams(adyen3DS2Configuration, null)

        val expected = getAdyen3DS2ComponentParams(
            shopperLocale = Locale.GERMAN,
            environment = Environment.EUROPE,
            clientKey = TEST_CLIENT_KEY_2,
            isAnalyticsEnabled = false,
            isCreatedByDropIn = true,
            amount = Amount(
                currency = "USD",
                value = 25_00L
            ),
        )

        Assertions.assertEquals(expected, params)
    }

    private fun getAdyen3DS2ConfigurationBuilder() = Adyen3DS2Configuration.Builder(
        shopperLocale = Locale.US,
        environment = Environment.TEST,
        clientKey = TEST_CLIENT_KEY_1
    )

    private fun getAdyen3DS2ComponentParams(
        shopperLocale: Locale = Locale.US,
        environment: Environment = Environment.TEST,
        clientKey: String = TEST_CLIENT_KEY_1,
        isAnalyticsEnabled: Boolean = true,
        isCreatedByDropIn: Boolean = false,
        amount: Amount = Amount.EMPTY,
        uiCustomization: UiCustomization? = null,
    ) = Adyen3DS2ComponentParams(
        shopperLocale = shopperLocale,
        environment = environment,
        clientKey = clientKey,
        isAnalyticsEnabled = isAnalyticsEnabled,
        isCreatedByDropIn = isCreatedByDropIn,
        amount = amount,
        uiCustomization = uiCustomization,
    )

    companion object {
        private const val TEST_CLIENT_KEY_1 = "test_qwertyuiopasdfghjklzxcvbnmqwerty"
        private const val TEST_CLIENT_KEY_2 = "live_qwertyui34566776787zxcvbnmqwerty"
    }
}