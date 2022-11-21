/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 1/9/2022.
 */

package com.adyen.checkout.components.ui.view

import com.adyen.checkout.components.ui.R
import com.adyen.checkout.components.ui.ViewProvider

interface ComponentViewType {
    val viewProvider: ViewProvider
}

interface ButtonComponentViewType : ComponentViewType {
    val buttonTextResId: Int
        get() = R.string.pay_button
}
