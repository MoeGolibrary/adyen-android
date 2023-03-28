/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by onurk on 28/2/2023.
 */

package com.adyen.checkout.ach.internal.ui

import app.cash.turbine.test
import com.adyen.checkout.ach.ACHDirectDebitConfiguration
import com.adyen.checkout.ach.internal.ui.model.ACHDirectDebitComponentParamsMapper
import com.adyen.checkout.components.core.OrderRequest
import com.adyen.checkout.components.core.StoredPaymentMethod
import com.adyen.checkout.components.core.internal.PaymentObserverRepository
import com.adyen.checkout.components.core.internal.data.api.AnalyticsRepository
import com.adyen.checkout.core.Environment
import com.adyen.checkout.test.TestDispatcherExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class, TestDispatcherExtension::class)
internal class StoredACHDirectDebitDelegateTest(
    @Mock private val analyticsRepository: AnalyticsRepository
) {
    private lateinit var delegate: ACHDirectDebitDelegate

    @BeforeEach
    fun setUp() {
        delegate = createAchDelegate()
    }

    @Nested
    @DisplayName("when creating component state and")
    inner class CreateComponentStateTest {
        @Test
        fun `when delegate is created , then component state should be valid`() = runTest {
            val componentState = delegate.componentStateFlow.first()
            assertTrue(componentState.isValid)
        }
    }

    @Test
    fun `when delegate is initialized then submit handler onSubmit is called`() = runTest {
        delegate.submitFlow.test {
            val coroutineScope = CoroutineScope(UnconfinedTestDispatcher())
            delegate.initialize(coroutineScope)
            assertEquals(delegate.componentStateFlow.first(), expectMostRecentItem())
        }
    }

    private fun getAchConfigurationBuilder() = ACHDirectDebitConfiguration.Builder(
        shopperLocale = Locale.US,
        environment = Environment.TEST,
        clientKey = TEST_CLIENT_KEY,
    )

    private fun createAchDelegate(
        paymentMethod: StoredPaymentMethod = StoredPaymentMethod(id = STORED_ID),
        analyticsRepository: AnalyticsRepository = this.analyticsRepository,
        configuration: ACHDirectDebitConfiguration = getAchConfigurationBuilder().build(),
        order: OrderRequest? = TEST_ORDER,
    ) = StoredACHDirectDebitDelegate(
        observerRepository = PaymentObserverRepository(),
        storedPaymentMethod = paymentMethod,
        analyticsRepository = analyticsRepository,
        componentParams = ACHDirectDebitComponentParamsMapper(null, null).mapToParams(configuration, null),
        order = order
    )

    companion object {
        private const val TEST_CLIENT_KEY = "test_qwertyuiopasdfghjklzxcvbnmqwerty"
        private val TEST_ORDER = OrderRequest("PSP", "ORDER_DATA")
        private const val STORED_ID = "Stored_id"
    }
}
