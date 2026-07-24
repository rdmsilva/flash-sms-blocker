package com.flashblocker;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementação em memória de SharedPreferences para os testes de unidade —
 * evita depender do runtime Android (e de frameworks de mock).
 */
class FakeSharedPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();

    @Override
    public Map<String, ?> getAll() {
        return new HashMap<>(values);
    }

    @Override
    public String getString(String key, String defValue) {
        Object v = values.get(key);
        return v != null ? (String) v : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object v = values.get(key);
        return v != null ? (Set<String>) v : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object v = values.get(key);
        return v != null ? (Integer) v : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object v = values.get(key);
        return v != null ? (Long) v : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object v = values.get(key);
        return v != null ? (Float) v : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object v = values.get(key);
        return v != null ? (Boolean) v : defValue;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        // não usado pelo app
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        // não usado pelo app
    }

    private class FakeEditor implements Editor {
        private final Map<String, Object> pending = new HashMap<>();
        private final Set<String> removals = new HashSet<>();
        private boolean clearAll = false;

        @Override
        public Editor putString(String key, String value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            removals.add(key);
            return this;
        }

        @Override
        public Editor clear() {
            clearAll = true;
            return this;
        }

        @Override
        public boolean commit() {
            apply();
            return true;
        }

        @Override
        public void apply() {
            if (clearAll) values.clear();
            for (String key : removals) values.remove(key);
            values.putAll(pending);
        }
    }
}
