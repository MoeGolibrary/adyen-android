/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by oscars on 8/2/2023.
 */

package com.adyen.checkout.upi

import com.adyen.checkout.components.core.internal.ui.model.InputData

data class UpiInputData(
    val virtualPaymentAddress: String = ""
) : InputData
