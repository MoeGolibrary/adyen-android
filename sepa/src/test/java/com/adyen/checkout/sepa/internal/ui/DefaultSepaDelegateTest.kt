/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by ozgur on 21/7/2022.
 */

package com.adyen.checkout.sepa.internal.ui

import app.cash.turbine.test
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.analytics.AnalyticsRepository
import com.adyen.checkout.components.base.ButtonComponentParamsMapper
import com.adyen.checkout.components.model.paymentmethods.PaymentMethod
import com.adyen.checkout.components.model.payments.request.Order
import com.adyen.checkout.components.model.payments.request.OrderRequest
import com.adyen.checkout.components.model.payments.request.SepaPaymentMethod
import com.adyen.checkout.components.repository.PaymentObserverRepository
import com.adyen.checkout.components.ui.SubmitHandler
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.sepa.SepaConfiguration
import com.adyen.checkout.sepa.internal.ui.model.SepaOutputData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class)
internal class DefaultSepaDelegateTest(
    @Mock private val analyticsRepository: AnalyticsRepository,
    @Mock private val submitHandler: SubmitHandler<PaymentComponentState<SepaPaymentMethod>>,
) {

    private lateinit var delegate: DefaultSepaDelegate

    @BeforeEach
    fun before() {
        delegate = createSepaDelegate()
    }

    @Nested
    @DisplayName("when input data changes and")
    inner class InputDataChangedTest {

        @Test
        fun `everything is good, then output data should be propagated`() = runTest {
            delegate.outputDataFlow.test {
                skipItems(1)
                delegate.updateInputData {
                    name = "name"
                    iban = "NL02ABNA0123456789"
                }

                with(awaitItem()) {
                    assertEquals("name", ownerNameField.value)
                    assertEquals("NL02ABNA0123456789", ibanNumberField.value)
                }

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `when creating component state is successful, then the state is propagated`() = runTest {
        delegate.componentStateFlow.test {
            skipItems(1)
            delegate.updateComponentState(SepaOutputData("name", "NL02ABNA0123456789"))

            with(awaitItem()) {
                assertTrue(data.paymentMethod is SepaPaymentMethod)
                assertTrue(isInputValid)
                assertTrue(isReady)
                assertEquals("name", data.paymentMethod?.ownerName)
                assertEquals("NL02ABNA0123456789", data.paymentMethod?.iban)
                assertEquals(TEST_ORDER, data.order)
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when delegate is initialized then analytics event is sent`() = runTest {
        delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
        verify(analyticsRepository).sendAnalyticsEvent()
    }

    @Nested
    inner class SubmitButtonVisibilityTest {

        @Test
        fun `when submit button is configured to be hidden, then it should not show`() {
            delegate = createSepaDelegate(
                configuration = getDefaultSepaConfigurationBuilder()
                    .setSubmitButtonVisible(false)
                    .build()
            )

            Assertions.assertFalse(delegate.shouldShowSubmitButton())
        }

        @Test
        fun `when submit button is configured to be visible, then it should show`() {
            delegate = createSepaDelegate(
                configuration = getDefaultSepaConfigurationBuilder()
                    .setSubmitButtonVisible(true)
                    .build()
            )

            assertTrue(delegate.shouldShowSubmitButton())
        }
    }

    @Nested
    inner class SubmitHandlerTest {

        @Test
        fun `when delegate is initialized then submit handler event is initialized`() = runTest {
            val coroutineScope = CoroutineScope(UnconfinedTestDispatcher())
            delegate.initialize(coroutineScope)
            verify(submitHandler).initialize(coroutineScope, delegate.componentStateFlow)
        }

        @Test
        fun `when delegate setInteractionBlocked is called then submit handler setInteractionBlocked is called`() =
            runTest {
                delegate.setInteractionBlocked(true)
                verify(submitHandler).setInteractionBlocked(true)
            }

        @Test
        fun `when delegate onSubmit is called then submit handler onSubmit is called`() = runTest {
            delegate.componentStateFlow.test {
                delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
                delegate.onSubmit()
                verify(submitHandler).onSubmit(expectMostRecentItem())
            }
        }
    }

    private fun createSepaDelegate(
        configuration: SepaConfiguration = getDefaultSepaConfigurationBuilder().build(),
        order: Order? = TEST_ORDER,
    ) = DefaultSepaDelegate(
        observerRepository = PaymentObserverRepository(),
        paymentMethod = PaymentMethod(),
        order = order,
        componentParams = ButtonComponentParamsMapper().mapToParams(configuration),
        analyticsRepository = analyticsRepository,
        submitHandler = submitHandler
    )

    private fun getDefaultSepaConfigurationBuilder() = SepaConfiguration.Builder(
        Locale.US,
        Environment.TEST,
        TEST_CLIENT_KEY
    )

    companion object {
        private const val TEST_CLIENT_KEY = "test_qwertyuiopasdfghjklzxcvbnmqwerty"
        private val TEST_ORDER = OrderRequest("PSP", "ORDER_DATA")
    }
}