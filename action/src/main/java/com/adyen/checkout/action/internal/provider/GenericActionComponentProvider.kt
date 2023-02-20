/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 23/8/2022.
 */

package com.adyen.checkout.action.internal.provider

import android.app.Application
import androidx.annotation.RestrictTo
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.adyen.checkout.action.GenericActionComponent
import com.adyen.checkout.action.GenericActionConfiguration
import com.adyen.checkout.action.internal.DefaultActionHandlingComponent
import com.adyen.checkout.action.internal.ui.ActionDelegateProvider
import com.adyen.checkout.action.internal.ui.DefaultGenericActionDelegate
import com.adyen.checkout.action.internal.ui.GenericActionDelegate
import com.adyen.checkout.components.ActionComponentProvider
import com.adyen.checkout.components.base.ActionComponentCallback
import com.adyen.checkout.components.base.ComponentParams
import com.adyen.checkout.components.base.DefaultActionComponentEventHandler
import com.adyen.checkout.components.base.GenericComponentParamsMapper
import com.adyen.checkout.components.base.lifecycle.get
import com.adyen.checkout.components.base.lifecycle.viewModelFactory
import com.adyen.checkout.components.model.payments.response.Action
import com.adyen.checkout.components.model.payments.response.AwaitAction
import com.adyen.checkout.components.model.payments.response.QrCodeAction
import com.adyen.checkout.components.model.payments.response.RedirectAction
import com.adyen.checkout.components.model.payments.response.SdkAction
import com.adyen.checkout.components.model.payments.response.Threeds2Action
import com.adyen.checkout.components.model.payments.response.Threeds2ChallengeAction
import com.adyen.checkout.components.model.payments.response.Threeds2FingerprintAction
import com.adyen.checkout.components.model.payments.response.VoucherAction
import com.adyen.checkout.components.repository.ActionObserverRepository

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class GenericActionComponentProvider(
    private val overrideComponentParams: ComponentParams? = null
) : ActionComponentProvider<GenericActionComponent, GenericActionConfiguration, GenericActionDelegate> {

    private val componentParamsMapper = GenericComponentParamsMapper()

    override fun get(
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        lifecycleOwner: LifecycleOwner,
        application: Application,
        configuration: GenericActionConfiguration,
        callback: ActionComponentCallback,
        key: String?,
    ): GenericActionComponent {
        val genericActionFactory = viewModelFactory(savedStateRegistryOwner, null) { savedStateHandle ->
            val genericActionDelegate = getDelegate(configuration, savedStateHandle, application)
            GenericActionComponent(
                genericActionDelegate = genericActionDelegate,
                actionHandlingComponent = DefaultActionHandlingComponent(genericActionDelegate, null),
                actionComponentEventHandler = DefaultActionComponentEventHandler(callback)
            )
        }
        return ViewModelProvider(viewModelStoreOwner, genericActionFactory)[key, GenericActionComponent::class.java]
            .also { component ->
                component.observe(lifecycleOwner, component.actionComponentEventHandler::onActionComponentEvent)
            }
    }

    override fun getDelegate(
        configuration: GenericActionConfiguration,
        savedStateHandle: SavedStateHandle,
        application: Application
    ): GenericActionDelegate {
        val componentParams = componentParamsMapper.mapToParams(configuration, overrideComponentParams)
        return DefaultGenericActionDelegate(
            observerRepository = ActionObserverRepository(),
            savedStateHandle = savedStateHandle,
            configuration = configuration,
            componentParams = componentParams,
            actionDelegateProvider = ActionDelegateProvider(componentParams)
        )
    }

    override val supportedActionTypes: List<String>
        get() = listOf(
            AwaitAction.ACTION_TYPE,
            QrCodeAction.ACTION_TYPE,
            RedirectAction.ACTION_TYPE,
            Threeds2Action.ACTION_TYPE,
            Threeds2ChallengeAction.ACTION_TYPE,
            Threeds2FingerprintAction.ACTION_TYPE,
            VoucherAction.ACTION_TYPE,
            SdkAction.ACTION_TYPE,
        )

    override fun canHandleAction(action: Action): Boolean = getProvider(action).canHandleAction(action)

    override fun providesDetails(action: Action): Boolean = getProvider(action).providesDetails(action)

    private fun getProvider(action: Action): ActionComponentProvider<*, *, *> {
        return getActionProviderFor(action) ?: throw IllegalArgumentException("No provider available for this action")
    }
}