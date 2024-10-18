/*
 * Copyright (c) 2024 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by oscars on 27/2/2024.
 */

package com.adyen.checkout.components.core.internal.analytics

import androidx.annotation.VisibleForTesting
import com.adyen.checkout.components.core.internal.analytics.data.AnalyticsRepository
import com.adyen.checkout.components.core.internal.ui.model.AnalyticsParams
import com.adyen.checkout.components.core.internal.ui.model.AnalyticsParamsLevel
import com.adyen.checkout.core.AdyenLogLevel
import com.adyen.checkout.core.DispatcherProvider
import com.adyen.checkout.core.internal.util.adyenLog
import com.adyen.checkout.core.internal.util.runSuspendCatching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

internal class DefaultAnalyticsManager(
    private val analyticsRepository: AnalyticsRepository,
    private val analyticsParams: AnalyticsParams,
    private val coroutineDispatcher: CoroutineDispatcher = DispatcherProvider.IO,
) : AnalyticsManager {

    private var checkoutAttemptIdState: CheckoutAttemptIdState = CheckoutAttemptIdState.NotAvailable

    private var isInitialized: Boolean = false

    private var _coroutineScope: CoroutineScope? = null
    private val coroutineScope: CoroutineScope get() = requireNotNull(_coroutineScope)

    private var timerJob: Job? = null

    private var ownerReference: String? = null

    override fun initialize(owner: Any, coroutineScope: CoroutineScope) {
        if (isInitialized) {
            adyenLog(AdyenLogLevel.DEBUG) { "Already initialized, ignoring." }
            return
        }
        isInitialized = true

        ownerReference = owner::class.qualifiedName
        _coroutineScope = coroutineScope

        coroutineScope.launch(coroutineDispatcher) {
            runSuspendCatching {
                analyticsRepository.fetchCheckoutAttemptId()
            }.fold(
                onSuccess = { attemptId ->
                    checkoutAttemptIdState = attemptId?.let { id ->
                        CheckoutAttemptIdState.Available(id)
                    }?.also {
                        startTimer()
                    } ?: CheckoutAttemptIdState.Failed
                },
                onFailure = {
                    adyenLog(AdyenLogLevel.WARN, it) { "Failed to fetch checkoutAttemptId." }
                    checkoutAttemptIdState = CheckoutAttemptIdState.Failed
                },
            )
        }
    }

    override fun trackEvent(event: AnalyticsEvent) {
        if (cannotSendEvents()) {
            adyenLog(AdyenLogLevel.DEBUG) { "Not allowed to track events, ignoring." }
            return
        }
        coroutineScope.launch(coroutineDispatcher) {
            runSuspendCatching {
                analyticsRepository.storeEvent(event)

                if (event.shouldForceSend) {
                    sendEvents()
                    startTimer()
                }
            }.fold(
                onSuccess = { /* Not necessary */ },
                onFailure = { throwable -> adyenLog(AdyenLogLevel.WARN, throwable) { "Storing event failed" } },
            )
        }
    }

    private fun startTimer() {
        stopTimer()
        timerJob = coroutineScope.launch(coroutineDispatcher) {
            while (isActive) {
                delay(DISPATCH_INTERVAL_MILLIS)
                sendEvents()
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
    }

    private suspend fun sendEvents() {
        val checkoutAttemptIdState = checkoutAttemptIdState as? CheckoutAttemptIdState.Available
        if (checkoutAttemptIdState == null) {
            adyenLog(AdyenLogLevel.WARN) { "checkoutAttemptId should be available at this point." }
            return
        }

        runSuspendCatching {
            analyticsRepository.sendEvents(checkoutAttemptIdState.checkoutAttemptId)
        }.fold(
            onSuccess = { /* Not necessary */ },
            onFailure = { throwable -> adyenLog(AdyenLogLevel.WARN, throwable) { "Failed sending analytics events" } },
        )
    }

    override fun getCheckoutAttemptId(): String = when (val checkoutAttemptIdState = checkoutAttemptIdState) {
        is CheckoutAttemptIdState.Available -> checkoutAttemptIdState.checkoutAttemptId
        CheckoutAttemptIdState.Failed -> FAILED_CHECKOUT_ATTEMPT_ID
        CheckoutAttemptIdState.NotAvailable -> CHECKOUT_ATTEMPT_ID_NOT_FETCHED
    }

    private fun cannotSendEvents() = analyticsParams.level.priority <= AnalyticsParamsLevel.NONE.priority

    override fun clear(owner: Any) {
        if (ownerReference != owner::class.qualifiedName) {
            adyenLog(AdyenLogLevel.DEBUG) { "Clear called by not the original owner, ignoring." }
            return
        }

        adyenLog(AdyenLogLevel.DEBUG) { "Clearing analytics manager" }

        _coroutineScope = null
        checkoutAttemptIdState = CheckoutAttemptIdState.NotAvailable
        ownerReference = null
        isInitialized = false
        stopTimer()
        timerJob = null
    }

    companion object {
        @VisibleForTesting
        internal const val CHECKOUT_ATTEMPT_ID_NOT_FETCHED = "checkoutAttemptId-not-fetched"

        @VisibleForTesting
        internal const val FAILED_CHECKOUT_ATTEMPT_ID = "fetch-checkoutAttemptId-failed"

        @VisibleForTesting
        internal val DISPATCH_INTERVAL_MILLIS = 10.seconds.inWholeMilliseconds
    }
}
