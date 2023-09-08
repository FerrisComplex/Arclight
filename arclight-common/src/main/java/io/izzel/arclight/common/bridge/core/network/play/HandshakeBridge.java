package io.izzel.arclight.common.bridge.core.network.play;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public interface HandshakeBridge {
    JsonObject getRealJsonObject();
    Gson getGson();
}
