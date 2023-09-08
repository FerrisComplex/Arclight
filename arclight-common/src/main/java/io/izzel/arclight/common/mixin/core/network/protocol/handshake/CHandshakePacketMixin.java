package io.izzel.arclight.common.mixin.core.network.protocol.handshake;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import io.izzel.arclight.common.bridge.core.network.play.HandshakeBridge;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.NetworkHooks;
import org.spigotmc.SpigotConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Objects;

@Mixin(ClientIntentionPacket.class)
public class CHandshakePacketMixin implements HandshakeBridge {

    private static final String EXTRA_DATA = "extraData";
    private static final Gson GSON = new Gson();

    private JsonObject realObject = null;

    @Shadow public String hostName;

    @Shadow private String fmlVersion;

    @Redirect(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readUtf(I)Ljava/lang/String;"))
    private String arclight$bungeeHostname(FriendlyByteBuf packetBuffer, int maxLength) {
        return packetBuffer.readUtf(Short.MAX_VALUE);
    }

    @Redirect(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraftforge/network/NetworkHooks;getFMLVersion(Ljava/lang/String;)Ljava/lang/String;"))
    private String arclight$readFromProfile(String ip) {
        // ip is the raw hostname data
        try {
            JsonObject jo = GSON.fromJson(ip, JsonObject.class);
            this.realObject = jo;
            if (jo.has("h")) {
                this.arclight$host = jo.get("h").getAsString();
                return "FML3";
            }
        } catch (Throwable t) {}
        return NetworkHooks.getFMLVersion(ip);
    }

    private transient String arclight$host;

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"))
    private void arclight$writeBack(FriendlyByteBuf p_179801_, CallbackInfo ci) {
        if (arclight$host != null) {
            this.hostName = arclight$host;
            arclight$host = null;
        }
    }

    @Override
    public JsonObject getRealJsonObject() {
        return realObject;
    }

    @Override
    public Gson getGson() {
        return GSON;
    }
}
