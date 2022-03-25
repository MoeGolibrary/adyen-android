/*
 * Copyright (c) 2019 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by caiof on 26/4/2019.
 */
package com.adyen.checkout.issuerlist

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.api.ImageLoader.Companion.getInstance
import com.adyen.checkout.components.model.payments.request.IssuerListPaymentMethod
import com.adyen.checkout.components.ui.view.AdyenLinearLayout
import com.adyen.checkout.core.log.LogUtil.getTag
import com.adyen.checkout.core.log.Logger.d

@Suppress("TooManyFunctions")
abstract class IssuerListSpinnerView<
    IssuerListPaymentMethodT : IssuerListPaymentMethod,
    IssuerListComponentT : IssuerListComponent<IssuerListPaymentMethodT>
    >
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AdyenLinearLayout<IssuerListOutputData, IssuerListConfiguration, PaymentComponentState<IssuerListPaymentMethodT>, IssuerListComponentT>(
        context,
        attrs,
        defStyleAttr
    ),
    AdapterView.OnItemSelectedListener {

    private val idealInputData = IssuerListInputData()
    private lateinit var issuersSpinner: AppCompatSpinner
    private lateinit var issuersAdapter: IssuerListSpinnerAdapter

    init {
        LayoutInflater.from(getContext()).inflate(R.layout.issuer_list_spinner_view, this, true)
    }

    override fun initView() {
        issuersSpinner = findViewById<AppCompatSpinner?>(R.id.spinner_issuers).apply {
            adapter = issuersAdapter
            onItemSelectedListener = this@IssuerListSpinnerView
        }
    }

    override fun initLocalizedStrings(localizedContext: Context) {
        // no embedded localized strings on this view
    }

    override fun onComponentAttached() {
        issuersAdapter = IssuerListSpinnerAdapter(
            context, emptyList(),
            getInstance(context, component.configuration.environment),
            component.paymentMethodType,
            hideIssuersLogo()
        )
    }

    override fun observeComponentChanges(lifecycleOwner: LifecycleOwner) {
        component.issuersLiveData.observe(lifecycleOwner, createIssuersObserver())
    }

    override val isConfirmationRequired: Boolean
        get() = true

    override fun highlightValidationErrors() {
        // no implementation
    }

    open fun hideIssuersLogo(): Boolean {
        return false
    }

    private fun onIssuersChanged(issuerList: List<IssuerModel>) {
        issuersAdapter.updateIssuers(issuerList)
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        d(TAG, "onItemSelected - " + issuersAdapter.getItem(position).name)
        idealInputData.selectedIssuer = issuersAdapter.getItem(position)
        component.inputDataChanged(idealInputData)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        issuersSpinner.isEnabled = enabled
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // nothing changed
    }

    private fun createIssuersObserver(): Observer<List<IssuerModel>> {
        return Observer { issuerList: List<IssuerModel> -> onIssuersChanged(issuerList) }
    }

    companion object {
        private val TAG = getTag()
    }
}
