/*
 * Copyright (c) 2024 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 26/8/2024.
 */

package com.adyen.checkout.example.ui.settings

import com.adyen.checkout.components.core.AnalyticsLevel
import com.adyen.checkout.example.R
import com.adyen.checkout.example.data.storage.CardAddressMode
import com.adyen.checkout.example.data.storage.CardInstallmentOptionsMode
import com.adyen.checkout.example.data.storage.KeyValueStorage
import com.adyen.checkout.example.data.storage.ThreeDSMode
import com.adyen.checkout.example.ui.theme.UITheme
import com.adyen.checkout.example.ui.theme.UIThemeRepository
import javax.inject.Inject

internal class SettingsEditor @Inject constructor(
    private val keyValueStorage: KeyValueStorage,
    private val uiThemeRepository: UIThemeRepository,
) {
    fun getEditSettingsData(settingsItem: SettingsItem): EditSettingsData {
        return when (settingsItem.identifier) {
            SettingsIdentifier.MERCHANT_ACCOUNT -> {
                EditSettingsData.Text(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.merchant_account_title,
                    text = keyValueStorage.getMerchantAccount(),
                )
            }

            SettingsIdentifier.AMOUNT -> {
                EditSettingsData.Text(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.amount_value_title,
                    text = keyValueStorage.getAmount().value.toString(),
                    inputType = EditSettingsData.Text.InputType.INTEGER,
                )
            }

            SettingsIdentifier.CURRENCY -> {
                EditSettingsData.Text(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.currency_title,
                    text = keyValueStorage.getAmount().currency.orEmpty(),
                )
            }

            SettingsIdentifier.THREE_DS_MODE -> {
                EditSettingsData.SingleSelectList(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.threeds_mode_title,
                    items = SettingsLists.threeDSModes.entries.map {
                        EditSettingsData.SingleSelectList.Item(text = it.value, value = it.key.toString())
                    },
                )
            }

            SettingsIdentifier.SHOPPER_REFERENCE -> {
                EditSettingsData.Text(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.shopper_reference_title,
                    text = keyValueStorage.getShopperReference(),
                )
            }

            SettingsIdentifier.COUNTRY -> {
                EditSettingsData.Text(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.shopper_country_title,
                    text = keyValueStorage.getCountry(),
                )
            }

            SettingsIdentifier.SHOPPER_LOCALE -> {
                EditSettingsData.Text(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.shopper_locale_title,
                    text = keyValueStorage.getShopperLocale().orEmpty(),
                )
            }

            SettingsIdentifier.SHOPPER_EMAIL -> {
                EditSettingsData.Text(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.shopper_email_title,
                    text = keyValueStorage.getShopperEmail().orEmpty(),
                )
            }

            SettingsIdentifier.ADDRESS_MODE -> {
                EditSettingsData.SingleSelectList(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.card_address_form_title,
                    items = SettingsLists.cardAddressModes.entries.map {
                        EditSettingsData.SingleSelectList.Item(text = it.value, value = it.key.toString())
                    },
                )
            }

            SettingsIdentifier.INSTALLMENTS_MODE -> {
                EditSettingsData.SingleSelectList(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.card_installment_options_mode_title,
                    items = SettingsLists.cardInstallmentOptionsModes.entries.map {
                        EditSettingsData.SingleSelectList.Item(text = it.value, value = it.key.toString())
                    },
                )
            }

            SettingsIdentifier.INSTANT_PAYMENT_METHOD_TYPE -> {
                EditSettingsData.Text(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.instant_payment_method_type_title,
                    text = keyValueStorage.getInstantPaymentMethodType(),
                )
            }

            SettingsIdentifier.ANALYTICS_LEVEL -> {
                EditSettingsData.SingleSelectList(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.analytics_level_title,
                    items = SettingsLists.analyticsLevels.entries.map {
                        EditSettingsData.SingleSelectList.Item(text = it.value, value = it.key.toString())
                    },
                )
            }

            SettingsIdentifier.UI_THEME -> {
                EditSettingsData.SingleSelectList(
                    identifier = settingsItem.identifier,
                    titleResId = R.string.ui_theme_title,
                    items = SettingsLists.uiThemes.entries.map {
                        EditSettingsData.SingleSelectList.Item(text = it.value, value = it.key.toString())
                    },
                )
            }

            SettingsIdentifier.SHOW_INSTALLMENT_AMOUNT,
            SettingsIdentifier.SPLIT_CARD_FUNDING_SOURCES,
            SettingsIdentifier.REMOVE_STORED_PAYMENT_METHOD -> {
                error("Edit mode is not applicable with boolean settings")
            }
        }
    }

    fun editSetting(identifier: SettingsIdentifier, newValue: String) {
        val formattedValue = newValue.ifBlank { null }
        when (identifier) {
            SettingsIdentifier.MERCHANT_ACCOUNT -> {
                keyValueStorage.setMerchantAccount(formattedValue)
            }

            SettingsIdentifier.AMOUNT -> {
                keyValueStorage.setAmount(formattedValue?.toLong())
            }

            SettingsIdentifier.CURRENCY -> {
                keyValueStorage.setCurrency(formattedValue)
            }

            SettingsIdentifier.SHOPPER_REFERENCE -> {
                keyValueStorage.setShopperReference(formattedValue)
            }

            SettingsIdentifier.COUNTRY -> {
                keyValueStorage.setCountry(formattedValue)
            }

            SettingsIdentifier.SHOPPER_LOCALE -> {
                keyValueStorage.setShopperLocale(formattedValue)
            }

            SettingsIdentifier.SHOPPER_EMAIL -> {
                keyValueStorage.setShopperEmail(formattedValue)
            }

            SettingsIdentifier.INSTANT_PAYMENT_METHOD_TYPE -> {
                keyValueStorage.setInstantPaymentMethodType(formattedValue)
            }

            else -> error("This edit mode is only supported wth text type settings")
        }
    }

    fun editSetting(identifier: SettingsIdentifier, newValue: EditSettingsData.SingleSelectList.Item) {
        when (identifier) {
            SettingsIdentifier.THREE_DS_MODE -> {
                val threeDSMode = ThreeDSMode.valueOf(newValue.value)
                keyValueStorage.setThreeDSMode(threeDSMode)
            }

            SettingsIdentifier.ADDRESS_MODE -> {
                val cardAddressMode = CardAddressMode.valueOf(newValue.value)
                keyValueStorage.setCardAddressMode(cardAddressMode)
            }

            SettingsIdentifier.INSTALLMENTS_MODE -> {
                val cardInstallmentOptionsMode = CardInstallmentOptionsMode.valueOf(newValue.value)
                keyValueStorage.setInstallmentOptionsMode(cardInstallmentOptionsMode)
            }

            SettingsIdentifier.ANALYTICS_LEVEL -> {
                val analyticsLevel = AnalyticsLevel.valueOf(newValue.value)
                keyValueStorage.setAnalyticsLevel(analyticsLevel)
            }

            SettingsIdentifier.UI_THEME -> {
                val uiTheme = UITheme.valueOf(newValue.value)
                uiThemeRepository.theme = uiTheme
            }

            else -> error("This edit mode is only supported wth boolean type settings")
        }
    }

    fun editSetting(identifier: SettingsIdentifier, newValue: Boolean) {
        when (identifier) {
            SettingsIdentifier.SHOW_INSTALLMENT_AMOUNT -> {
                keyValueStorage.setInstallmentAmountShown(newValue)
            }

            SettingsIdentifier.SPLIT_CARD_FUNDING_SOURCES -> {
                keyValueStorage.setSplitCardFundingSources(newValue)
            }

            SettingsIdentifier.REMOVE_STORED_PAYMENT_METHOD -> {
                keyValueStorage.setRemoveStoredPaymentMethodEnabled(newValue)
            }

            else -> error("This edit mode is only supported wth boolean type settings")
        }
    }
}
