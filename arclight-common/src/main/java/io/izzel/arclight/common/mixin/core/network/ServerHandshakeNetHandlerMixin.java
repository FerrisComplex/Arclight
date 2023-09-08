package io.izzel.arclight.common.mixin.core.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import com.mojang.util.UUIDTypeAdapter;
import io.izzel.arclight.common.bridge.core.network.NetworkManagerBridge;
import io.izzel.arclight.common.bridge.core.network.play.HandshakeBridge;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.bukkit.Bukkit;
import org.spigotmc.SpigotConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;

@Mixin(ServerHandshakePacketListenerImpl.class)
public class ServerHandshakeNetHandlerMixin {

    private static final Gson gson = new Gson();
    private static final HashMap<InetAddress, Long> throttleTracker = new HashMap<>();
    private static int throttleCounter = 0;

    // @formatter:off
    @Shadow @Final private Connection connection;
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private static Component IGNORE_STATUS_REASON;
    // @formatter:on

    /**
     * @author IzzelAliz
     * @reason
     */
    @Overwrite
    public void handleIntention(ClientIntentionPacket packetIn) {
        if (!ServerLifecycleHooks.handleServerLogin(packetIn, this.connection)) return;


        switch (packetIn.getIntention()) {
            case LOGIN: {
                this.connection.setProtocol(ConnectionProtocol.LOGIN);

                try {
                    long currentTime = System.currentTimeMillis();
                    long connectionThrottle = Bukkit.getServer().getConnectionThrottle();
                    InetAddress address = ((InetSocketAddress) this.connection.getRemoteAddress()).getAddress();
                    synchronized (throttleTracker) {
                        if (throttleTracker.containsKey(address) && !"127.0.0.1".equals(address.getHostAddress()) && currentTime - throttleTracker.get(address) < connectionThrottle) {
                            throttleTracker.put(address, currentTime);
                            var component = Component.translatable("Connection throttled! Please wait before reconnecting.");
                            this.connection.send(new ClientboundLoginDisconnectPacket(component));
                            this.connection.disconnect(component);
                            return;
                        }
                        throttleTracker.put(address, currentTime);
                        ++throttleCounter;
                        if (throttleCounter > 200) {
                            throttleCounter = 0;
                            throttleTracker.entrySet().removeIf(entry -> entry.getValue() > connectionThrottle);
                        }
                    }
                } catch (Throwable t) {
                    LogManager.getLogger().debug("Failed to check connection throttle", t);
                }


                if (packetIn.getProtocolVersion() > SharedConstants.getCurrentVersion().getProtocolVersion()) {
                    var component = Component.translatable(MessageFormat.format(SpigotConfig.outdatedServerMessage.replaceAll("'", "''"), SharedConstants.getCurrentVersion().getName()));
                    this.connection.send(new ClientboundLoginDisconnectPacket(component));
                    this.connection.disconnect(component);
                    break;
                }
                if (packetIn.getProtocolVersion() < SharedConstants.getCurrentVersion().getProtocolVersion()) {
                    var component = Component.translatable(MessageFormat.format(SpigotConfig.outdatedClientMessage.replaceAll("'", "''"), SharedConstants.getCurrentVersion().getName()));
                    this.connection.send(new ClientboundLoginDisconnectPacket(component));
                    this.connection.disconnect(component);
                    break;
                }
                this.connection.setListener(new ServerLoginPacketListenerImpl(this.server, this.connection));


                if (SpigotConfig.bungee) {
                    JsonObject data = ((HandshakeBridge) packetIn).getRealJsonObject();

                    packetIn.hostName = data.get("h").getAsString();
                    this.connection.address = new InetSocketAddress(data.get("rIp").getAsString(), ((InetSocketAddress) this.connection.getRemoteAddress()).getPort());
                    ((NetworkManagerBridge) this.connection).bridge$setSpoofedUUID(UUIDTypeAdapter.fromString(data.get("u").getAsString()));
                    if (data.has("p")) {

                        ArrayList<Property> properties = new ArrayList<>();
                        for(JsonElement s : data.get("p").getAsJsonArray()) {
                            JsonObject prop = s.getAsJsonObject();
                            if(prop.has("s") && !prop.get("s").getAsString().isEmpty())
                                properties.add(new Property(prop.get("n").getAsString(), prop.get("v").getAsString(), prop.get("s").getAsString()));
                            else
                                properties.add(new Property(prop.get("n").getAsString(), prop.get("v").getAsString()));
                        }

                        Property[] array = properties.toArray(new Property[0]);
                        ((NetworkManagerBridge) this.connection).bridge$setSpoofedProfile(array);

                    }
                    System.out.println("Completed set all spoofed arguments from " + data.toString());
                }

                break;
            }
            case STATUS: {
                ServerStatus serverstatus = this.server.getStatus();
                if (this.server.repliesToStatus() && serverstatus != null) {
                    this.connection.setProtocol(ConnectionProtocol.STATUS);
                    this.connection.setListener(new ServerStatusPacketListenerImpl(serverstatus, this.connection));
                } else {
                    this.connection.disconnect(IGNORE_STATUS_REASON);
                }
                break;
            }
            default: {
                throw new UnsupportedOperationException("Invalid intention " + packetIn.getIntention());
            }
        }
    }
}
