package org.json;

// Harness stub: only NeuroStorage's legacy migration names it, which no benchmark runs.
public class JSONObject {
    public JSONObject(String json) { throw new UnsupportedOperationException("harness stub"); }
    public JSONObject optJSONObject(String key) { return null; }
    public JSONArray optJSONArray(String key) { return null; }
    public java.util.Iterator<String> keys() { return java.util.Collections.emptyIterator(); }
    public double getDouble(String key) { return 0; }
    public int getInt(String key) { return 0; }
    public int optInt(String key, int fallback) { return fallback; }
    public double optDouble(String key, double fallback) { return fallback; }
    public boolean optBoolean(String key, boolean fallback) { return fallback; }
}
