/*
 * Copyright (c) 2021 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by caiof on 14/1/2021.
 */

package com.adyen.checkout.cse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.adyen.checkout.core.exception.NoConstructorException;
import com.adyen.checkout.cse.exception.EncryptionException;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

// UnsynchronizedStaticDateFormatter is a deprecated check.
@SuppressWarnings("PMD.UnsynchronizedStaticDateFormatter")
public final class CardEncrypter {

    private static final String CARD_NUMBER_KEY = "number";
    private static final String EXPIRY_MONTH_KEY = "expiryMonth";
    private static final String EXPIRY_YEAR_KEY = "expiryYear";
    private static final String CVC_KEY = "cvc";
    private static final String HOLDER_NAME_KEY = "holderName";
    private static final String GENERATION_TIME_KEY = "generationtime";

    private static final String ENCRYPTION_FAILED_MESSAGE = "Encryption failed.";

    static final SimpleDateFormat GENERATION_DATE_FORMAT;

    static {
        GENERATION_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        GENERATION_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private CardEncrypter() {
        throw new NoConstructorException();
    }

    /**
     * Encrypts the available card data from {@link UnencryptedCard} into individual encrypted blocks.
     *
     * @param unencryptedCard The card data to be encrypted.
     * @param publicKey     The key to be used for encryption.
     * @return An {@link EncryptedCard} object with each encrypted field.
     * @throws EncryptionException in case the encryption fails.
     */
    @WorkerThread
    @NonNull
    public static EncryptedCard encryptFields(
            @NonNull final UnencryptedCard unencryptedCard,
            @NonNull final String publicKey
    ) throws EncryptionException {
        try {
            final String formattedGenerationTime =
                    GENERATION_DATE_FORMAT.format(assureGenerationTime(unencryptedCard.getGenerationTime()));
            final ClientSideEncrypter encrypter = new ClientSideEncrypter(publicKey);

            final String encryptedNumber;
            final String encryptedExpiryMonth;
            final String encryptedExpiryYear;
            final String encryptedSecurityCode;

            JSONObject jsonToEncrypt;

            if (unencryptedCard.getNumber() != null) {
                try {
                    jsonToEncrypt = new JSONObject();
                    jsonToEncrypt.put(CARD_NUMBER_KEY, unencryptedCard.getNumber());
                    jsonToEncrypt.put(GENERATION_TIME_KEY, formattedGenerationTime);

                    encryptedNumber = encrypter.encrypt(jsonToEncrypt.toString());
                } catch (JSONException e) {
                    throw new EncryptionException(ENCRYPTION_FAILED_MESSAGE, e);
                }
            } else {
                encryptedNumber = null;
            }

            if (unencryptedCard.getExpiryMonth() != null && unencryptedCard.getExpiryYear() != null) {
                try {
                    jsonToEncrypt = new JSONObject();
                    jsonToEncrypt.put(EXPIRY_MONTH_KEY, unencryptedCard.getExpiryMonth());
                    jsonToEncrypt.put(GENERATION_TIME_KEY, formattedGenerationTime);

                    encryptedExpiryMonth = encrypter.encrypt(jsonToEncrypt.toString());
                } catch (JSONException e) {
                    throw new EncryptionException(ENCRYPTION_FAILED_MESSAGE, e);
                }

                try {
                    jsonToEncrypt = new JSONObject();
                    jsonToEncrypt.put(EXPIRY_YEAR_KEY, unencryptedCard.getExpiryYear());
                    jsonToEncrypt.put(GENERATION_TIME_KEY, formattedGenerationTime);

                    encryptedExpiryYear = encrypter.encrypt(jsonToEncrypt.toString());
                } catch (JSONException e) {
                    throw new EncryptionException(ENCRYPTION_FAILED_MESSAGE, e);
                }
            } else if (unencryptedCard.getExpiryMonth() == null && unencryptedCard.getExpiryYear() == null) {
                encryptedExpiryMonth = null;
                encryptedExpiryYear = null;
            } else {
                throw new EncryptionException("Both expiryMonth and expiryYear need to be set for encryption.", null);
            }

            if (unencryptedCard.getCvc() != null) {
                try {
                    jsonToEncrypt = new JSONObject();
                    jsonToEncrypt.put(CVC_KEY, unencryptedCard.getCvc());
                    jsonToEncrypt.put(GENERATION_TIME_KEY, formattedGenerationTime);

                    encryptedSecurityCode = encrypter.encrypt(jsonToEncrypt.toString());
                } catch (JSONException e) {
                    throw new EncryptionException(ENCRYPTION_FAILED_MESSAGE, e);
                }
            } else {
                encryptedSecurityCode = null;
            }

            return new EncryptedCard(encryptedNumber, encryptedExpiryMonth, encryptedExpiryYear, encryptedSecurityCode);

        } catch (EncryptionException | IllegalStateException e) {
            throw new EncryptionException(e.getMessage() == null ? "No message." : e.getMessage(), e);
        }
    }

    /**
     * Encrypts all the card data present in {@link UnencryptedCard} into a single block of content.
     *
     * @param unencryptedCard The card data to be encrypted.
     * @param publicKey     The key to be used for encryption.
     * @return The encrypted card data String.
     * @throws EncryptionException in case the encryption fails.
     */
    @NonNull
    @WorkerThread
    public static String encrypt(
            @NonNull final UnencryptedCard unencryptedCard,
            @NonNull final  String publicKey
    ) throws EncryptionException {
        final JSONObject cardJson = new JSONObject();
        String encryptedData = null;

        try {
            cardJson.put(CARD_NUMBER_KEY, unencryptedCard.getNumber());
            cardJson.put(EXPIRY_MONTH_KEY, unencryptedCard.getExpiryMonth());
            cardJson.put(EXPIRY_YEAR_KEY, unencryptedCard.getExpiryYear());
            cardJson.put(CVC_KEY, unencryptedCard.getCvc());
            cardJson.put(HOLDER_NAME_KEY, unencryptedCard.getCardHolderName());
            final Date generationTime = assureGenerationTime(unencryptedCard.getGenerationTime());
            cardJson.put(GENERATION_TIME_KEY, GENERATION_DATE_FORMAT.format(generationTime));

            final ClientSideEncrypter encrypter = new ClientSideEncrypter(publicKey);
            encryptedData = encrypter.encrypt(cardJson.toString());
        } catch (JSONException e) {
            throw new EncryptionException("Failed to created encrypted JSON data.", e);
        }

        return encryptedData;
    }

    private static Date assureGenerationTime(@Nullable Date generationTime) {
        if (generationTime == null) {
            return new Date();
        }

        return generationTime;
    }
}
