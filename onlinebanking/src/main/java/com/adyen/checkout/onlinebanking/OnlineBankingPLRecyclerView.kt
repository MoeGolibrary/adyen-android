/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by atef on 10/8/2022.
 */

package com.adyen.checkout.onlinebanking

import android.content.Context
import android.util.AttributeSet
import com.adyen.checkout.components.model.payments.request.OnlineBankingPLPaymentMethods
import com.adyen.checkout.issuerlist.IssuerListRecyclerView

class OnlineBankingPLRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : IssuerListRecyclerView<OnlineBankingPLPaymentMethods, OnlineBankingPLComponent>(context, attrs, defStyleAttr)
