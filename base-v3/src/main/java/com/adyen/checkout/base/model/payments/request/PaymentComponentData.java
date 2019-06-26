/*
 * Copyright (c) 2019 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by caiof on 28/5/2019.
 */

package com.adyen.checkout.base.model.payments.request;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.adyen.checkout.core.code.Lint;
import com.adyen.checkout.core.exeption.CheckoutException;
import com.adyen.checkout.core.model.ModelObject;
import com.adyen.checkout.core.util.StringUtil;

import org.json.JSONObject;

/**
 * This class is a top level abstraction for data objects that can be serialized to the paymentMethod parameter on a payments/ call.
 * The {@link #SERIALIZER} object can serialize this to a {@link JSONObject} with the corresponding data.
 *
 * <p/>
 * Alternatively you can use other parsing libraries if they support polymorphism.
 */
@SuppressWarnings("MemberName")
public abstract class PaymentComponentData extends ModelObject {

    public static final String TYPE = "type";

    @NonNull
    public static final Serializer<PaymentComponentData> SERIALIZER = new Serializer<PaymentComponentData>() {

        @NonNull
        @Override
        public JSONObject serialize(@NonNull PaymentComponentData modelObject) {
            final String actionType = modelObject.getType();
            if (!StringUtil.hasContent(actionType)) {
                throw new CheckoutException("PaymentMethod type not found");
            }
            //noinspection unchecked
            final Serializer<PaymentComponentData> serializer = (Serializer<PaymentComponentData>) getChildSerializer(actionType);
            return serializer.serialize(modelObject);
        }

        @NonNull
        @Override
        public PaymentComponentData deserialize(@NonNull JSONObject jsonObject) {
            final String actionType = jsonObject.optString(TYPE);
            if (!StringUtil.hasContent(actionType)) {
                throw new CheckoutException("PaymentMethod type not found");
            }
            //noinspection unchecked
            final Serializer<PaymentComponentData> serializer = (Serializer<PaymentComponentData>) getChildSerializer(actionType);
            return serializer.deserialize(jsonObject);
        }
    };

    private String type;

    @SuppressWarnings(Lint.SYNTHETIC)
    @NonNull
    static Serializer<? extends PaymentComponentData> getChildSerializer(@NonNull String paymentMethodType) {
        switch (paymentMethodType) {
            case IdealPaymentMethod.PAYMENT_METHOD_TYPE:
                return IdealPaymentMethod.SERIALIZER;
            case CardPaymentMethod.PAYMENT_METHOD_TYPE:
                return CardPaymentMethod.SERIALIZER;
            case MolpayPaymentMethod.PAYMENT_METHOD_TYPE:
                return MolpayPaymentMethod.SERIALIZER;
            case DotpayPaymentMethod.PAYMENT_METHOD_TYPE:
                return DotpayPaymentMethod.SERIALIZER;
            case EPSPaymentMethod.PAYMENT_METHOD_TYPE:
                return EPSPaymentMethod.SERIALIZER;
            case OpenBankingPaymentMethod.PAYMENT_METHOD_TYPE:
                return OpenBankingPaymentMethod.SERIALIZER;
            case EntercashPaymentMethod.PAYMENT_METHOD_TYPE:
                return EntercashPaymentMethod.SERIALIZER;
            default:
                return GenericPaymentMethod.SERIALIZER;
        }
    }

    @Nullable
    public String getType() {
        return type;
    }

    public void setType(@Nullable String type) {
        this.type = type;
    }
}