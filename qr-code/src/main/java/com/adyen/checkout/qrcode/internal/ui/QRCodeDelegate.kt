/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by oscars on 12/8/2022.
 */

package com.adyen.checkout.qrcode.internal.ui

import androidx.annotation.RestrictTo
import com.adyen.checkout.components.base.ActionDelegate
import com.adyen.checkout.components.base.DetailsEmittingDelegate
import com.adyen.checkout.components.base.IntentHandlingDelegate
import com.adyen.checkout.components.base.StatusPollingDelegate
import com.adyen.checkout.components.base.ViewableDelegate
import com.adyen.checkout.components.ui.ViewProvidingDelegate
import com.adyen.checkout.qrcode.internal.ui.model.QRCodeOutputData
import com.adyen.checkout.qrcode.internal.ui.model.QrCodeUIEvent
import kotlinx.coroutines.flow.Flow

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface QRCodeDelegate :
    ActionDelegate,
    DetailsEmittingDelegate,
    ViewableDelegate<QRCodeOutputData>,
    IntentHandlingDelegate,
    StatusPollingDelegate,
    ViewProvidingDelegate {

    val eventFlow: Flow<QrCodeUIEvent>

    fun downloadQRImage()
}