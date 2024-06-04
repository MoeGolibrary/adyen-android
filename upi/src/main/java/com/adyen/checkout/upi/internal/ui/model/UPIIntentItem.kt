/*
 * Copyright (c) 2024 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by ararat on 22/4/2024.
 */

package com.adyen.checkout.upi.internal.ui.model

import androidx.annotation.StringRes
import com.adyen.checkout.core.Environment

internal sealed class UPIIntentItem {

    abstract fun areItemsTheSame(newItem: UPIIntentItem): Boolean
    abstract fun areContentsTheSame(newItem: UPIIntentItem): Boolean
    abstract fun getChangePayload(newItem: UPIIntentItem): Any?

    data class PaymentApp(
        val id: String,
        val name: String,
        val environment: Environment,
    ) : UPIIntentItem() {
        override fun areItemsTheSame(newItem: UPIIntentItem) =
            newItem is PaymentApp &&
                id == newItem.id

        override fun areContentsTheSame(newItem: UPIIntentItem) =
            newItem is PaymentApp &&
                name == newItem.name &&
                environment == newItem.environment

        override fun getChangePayload(newItem: UPIIntentItem) = null
    }

    data object GenericApp : UPIIntentItem() {
        override fun areItemsTheSame(newItem: UPIIntentItem) = newItem is GenericApp
        override fun areContentsTheSame(newItem: UPIIntentItem) = newItem is GenericApp
        override fun getChangePayload(newItem: UPIIntentItem) = null
    }

    data class ManualInput(
        @StringRes val errorMessageResource: Int?
    ) : UPIIntentItem() {
        override fun areItemsTheSame(newItem: UPIIntentItem) = newItem is ManualInput
        override fun areContentsTheSame(newItem: UPIIntentItem) =
            newItem is ManualInput &&
                errorMessageResource == newItem.errorMessageResource

        // This has been implemented to avoid creating a new ViewHolder when the input field has validation error
        override fun getChangePayload(newItem: UPIIntentItem) =
            newItem is ManualInput &&
                errorMessageResource == newItem.errorMessageResource
    }
}
