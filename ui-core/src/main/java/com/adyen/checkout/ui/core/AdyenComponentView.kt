/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by atef on 21/2/2023.
 */
package com.adyen.checkout.ui.core

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.adyen.checkout.components.core.internal.Component
import com.adyen.checkout.components.core.internal.ui.ComponentDelegate
import com.adyen.checkout.components.core.internal.ui.model.ComponentParams
import com.adyen.checkout.core.internal.util.LogUtil
import com.adyen.checkout.core.internal.util.Logger
import com.adyen.checkout.ui.core.databinding.AdyenComponentViewBinding
import com.adyen.checkout.ui.core.internal.ui.AmountButtonComponentViewType
import com.adyen.checkout.ui.core.internal.ui.ButtonComponentViewType
import com.adyen.checkout.ui.core.internal.ui.ButtonDelegate
import com.adyen.checkout.ui.core.internal.ui.ComponentView
import com.adyen.checkout.ui.core.internal.ui.ComponentViewType
import com.adyen.checkout.ui.core.internal.ui.PaymentComponentUIEvent
import com.adyen.checkout.ui.core.internal.ui.UIStateDelegate
import com.adyen.checkout.ui.core.internal.ui.ViewProvidingDelegate
import com.adyen.checkout.ui.core.internal.ui.ViewableComponent
import com.adyen.checkout.ui.core.internal.util.PayButtonFormatter
import com.adyen.checkout.ui.core.internal.util.createLocalizedContext
import com.adyen.checkout.ui.core.internal.util.hideKeyboard
import com.adyen.checkout.ui.core.internal.util.resetFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.lang.ref.WeakReference

/**
 * A View that can display input and fill in details for a Component.
 */
class AdyenComponentView @JvmOverloads constructor(
    context: Context,
    private val attrs: AttributeSet? = null,
    private val defStyleAttr: Int = 0
) :
    LinearLayout(context, attrs, defStyleAttr) {

    private val binding: AdyenComponentViewBinding = AdyenComponentViewBinding.inflate(
        LayoutInflater.from(context),
        this
    )

    /**
     * Indicates if user interaction is blocked.
     */
    @Volatile
    private var isInteractionBlocked = false

    private var componentView: ComponentView? = null

    private var attachedComponent = WeakReference<Component?>(null)

    init {
        isVisible = isInEditMode
        orientation = VERTICAL
    }

    /**
     * Attach this view to a component to interact with.
     *
     * @param component      The component.
     * @param lifecycleOwner The lifecycle owner where the view is.
     */
    fun <T> attach(
        component: T,
        lifecycleOwner: LifecycleOwner
    ) where T : ViewableComponent, T : Component {
        if (component == attachedComponent.get()) return

        attachedComponent = WeakReference(component)

        component.viewFlow
            .onEach { componentViewType ->
                binding.frameLayoutComponentContainer.removeAllViews()

                if (componentViewType == null) {
                    Logger.i(TAG, "Component view type is null, ignoring.")
                    return@onEach
                }

                val delegate = component.delegate
                if (delegate !is ViewProvidingDelegate) {
                    Logger.i(TAG, "View attached to non viewable component, ignoring.")
                    return@onEach
                }

                loadView(
                    viewType = componentViewType,
                    delegate = delegate,
                    componentParams = delegate.componentParams,
                    coroutineScope = lifecycleOwner.lifecycleScope,
                )
            }
            .launchIn(lifecycleOwner.lifecycleScope)
        isVisible = true
    }

    private fun loadView(
        viewType: ComponentViewType,
        delegate: ComponentDelegate,
        componentParams: ComponentParams,
        coroutineScope: CoroutineScope,
    ) {
        val componentView = viewType.viewProvider.getView(viewType, context, attrs, defStyleAttr)
        this.componentView = componentView

        val localizedContext = context.createLocalizedContext(componentParams.shopperLocale)

        binding.payButton.setText(viewType, componentParams, localizedContext)

        val view = componentView.getView()
        binding.frameLayoutComponentContainer.addView(view)
        view.updateLayoutParams { width = LayoutParams.MATCH_PARENT }

        componentView.initView(delegate, coroutineScope, localizedContext)

        val buttonDelegate = (delegate as? ButtonDelegate)
        if (buttonDelegate?.isConfirmationRequired() == true) {
            val uiStateDelegate = (delegate as? UIStateDelegate)
            uiStateDelegate?.uiStateFlow?.onEach {
                setInteractionBlocked(it.isInteractionBlocked())
            }?.launchIn(coroutineScope)

            uiStateDelegate?.uiEventFlow?.onEach {
                when (it) {
                    PaymentComponentUIEvent.InvalidUI -> highlightValidationErrors()
                }
            }?.launchIn(coroutineScope)

            binding.payButton.isVisible = buttonDelegate.shouldShowSubmitButton()
            binding.payButton.setOnClickListener {
                buttonDelegate.onSubmit()
            }
        } else {
            binding.payButton.isVisible = false
            binding.payButton.setOnClickListener(null)
        }
    }

    private fun setInteractionBlocked(isInteractionBlocked: Boolean) {
        this.isInteractionBlocked = isInteractionBlocked

        binding.payButton.isEnabled = !isInteractionBlocked

        if (isInteractionBlocked) {
            resetFocus()
            hideKeyboard()
        }
    }

    private fun Button.setText(
        viewType: ComponentViewType,
        componentParams: ComponentParams,
        localizedContext: Context
    ) {
        if (viewType is AmountButtonComponentViewType) {
            text = PayButtonFormatter.getPayButtonText(
                amount = componentParams.amount,
                locale = componentParams.shopperLocale,
                localizedContext = localizedContext,
                emptyAmountStringResId = viewType.buttonTextResId
            )
        } else if (viewType is ButtonComponentViewType) {
            text = localizedContext.getString(viewType.buttonTextResId)
        }
    }

    /**
     * Highlight and focus on the current validation errors for the user to take action.
     * If the component doesn't need validation or if everything is already valid, nothing will happen.
     */
    fun highlightValidationErrors() {
        componentView?.highlightValidationErrors()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (isInteractionBlocked) return true
        return super.onInterceptTouchEvent(ev)
    }

    companion object {
        private val TAG = LogUtil.getTag()
    }
}