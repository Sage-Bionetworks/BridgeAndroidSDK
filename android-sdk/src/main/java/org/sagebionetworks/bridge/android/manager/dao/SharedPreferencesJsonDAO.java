package org.sagebionetworks.bridge.android.manager.dao;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;

import com.google.common.base.Function;
import com.google.gson.reflect.TypeToken;

import org.sagebionetworks.bridge.rest.RestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists objects in SharedPreferences via JSON serialization/deserialization. Transforms can be
 * applied to the JSON string, with the transform being applied to the JSON before saving to shared
 * prefs, or applied to the string after retrieving from saved prefs. Encryption/decryption are an
 * example of useful transforms to apply.
 */
@AnyThread
public class SharedPreferencesJsonDAO {
    private static final Logger logger = LoggerFactory.getLogger(SharedPreferencesJsonDAO.class);

    protected final SharedPreferences sharedPreferences;

    protected SharedPreferencesJsonDAO(Context applicationContext, String preferencesFile) {
        sharedPreferences = applicationContext
                .getSharedPreferences(preferencesFile, Context.MODE_PRIVATE);
    }

    protected void removeValue(String key) {
        logger.debug("removing key: " + key);

        sharedPreferences.edit().remove(key).apply();
    }

    protected <T> void setValue(String key, T value, Class<? super T> klass) {
        String json = RestUtils.GSON.toJson(value, klass);

        logger.debug("setting key: " + key + ", value: " + json);

        sharedPreferences.edit().putString(key, json).apply();
    }

    protected <T> T getValue(String key, Class<? extends T> klass) {
        String json = sharedPreferences.getString(key, null);

        logger.debug("getting key: " + key + ", value: " + json);

        return RestUtils.GSON.fromJson(json, klass);
    }

    protected <T> void setValue(String key, T value, TypeToken<? super T> type) {
        String json = RestUtils.GSON.toJson(value, type.getType());

        logger.debug("setting key: " + key + ", value: " + json);

        sharedPreferences.edit().putString(key, json).apply();
    }

    protected <T> T getValue(String key, TypeToken<? extends T> type) {
        String json = sharedPreferences.getString(key, null);

        logger.debug("getting key: " + key + ", value: " + json);

        return RestUtils.GSON.fromJson(json, type.getType());
    }

    protected <T> void setValue(String key, T value, TypeToken<? super T> type,
                                Function<String, String> transform) {
        String json = RestUtils.GSON.toJson(value, type.getType());

        logger.debug("setting key: " + key + ", value: " + json);

        json = transform.apply(json);

        sharedPreferences.edit().putString(key, json).apply();
    }

    protected <T> T getValue(String key, TypeToken<? extends T> type,
                             Function<String, String> transform) {
        String json = sharedPreferences.getString(key, null);

        json = transform.apply(json);

        logger.debug("getting key: " + key + ", value: " + json);

        return RestUtils.GSON.fromJson(json, type.getType());
    }

    protected <T> void setValue(String key, T value, Class<? super T> klass,
                                Function<String, String> transform) {
        String json = RestUtils.GSON.toJson(value, klass);

        logger.debug("setting key: " + key + ", value: " + json);

        json = transform.apply(json);

        sharedPreferences.edit().putString(key, json).apply();
    }

    protected <T> T getValue(String key, Class<? extends T> klass,
                             Function<String, String> transform) {
        String json = sharedPreferences.getString(key, null);

        json = transform.apply(json);

        logger.debug("getting key: " + key + ", value: " + json);

        return RestUtils.GSON.fromJson(json, klass);
    }
}
