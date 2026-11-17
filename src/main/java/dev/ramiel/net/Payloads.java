package dev.ramiel.net;

import dev.ramiel.RamielMod;
import dev.ramiel.Setting;
import dev.ramiel.client.ClientNet;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * All network messages in one file.
 *
 *   Fx            server -> client   "draw this effect here"
 *   Film          server -> client   "play this cutscene"
 *   Beacon        server -> client   "Ramiel is here" (lets far-away clients draw it on the horizon)
 *   SettingsOpen  server -> client   "open the settings screen with these values"
 *   SettingsSet   client -> server   "apply these values" (needs permission level 2 or the host)
 *
 * Client-side handling lives in {@link ClientNet} so this class never touches client-only code.
 */
public final class Payloads {
    private Payloads() {}

    // ------------------------------------------------------------------------------------------
    public record Fx(int kind, double x, double y, double z, double a, double b, double c, float f1, float f2)
            implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(RamielMod.id("fx"));
        public static final StreamCodec<ByteBuf, Fx> CODEC = StreamCodec.of(Fx::write, Fx::read);

        private static void write(ByteBuf buf, Fx p) {
            buf.writeInt(p.kind);
            buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
            buf.writeDouble(p.a); buf.writeDouble(p.b); buf.writeDouble(p.c);
            buf.writeFloat(p.f1); buf.writeFloat(p.f2);
        }

        private static Fx read(ByteBuf buf) {
            return new Fx(buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ------------------------------------------------------------------------------------------
    public record Film(int kind, int entityId, int aux) implements CustomPacketPayload {
        public static final Type<Film> TYPE = new Type<>(RamielMod.id("film"));
        public static final StreamCodec<ByteBuf, Film> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeInt(p.kind); buf.writeInt(p.entityId); buf.writeInt(p.aux); },
                buf -> new Film(buf.readInt(), buf.readInt(), buf.readInt()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ------------------------------------------------------------------------------------------
    public record Beacon(UUID id, double x, double y, double z, float aimYaw, float aimPitch, float spin,
                         int formFrom, int formTo, float morph, boolean alive)
            implements CustomPacketPayload {
        public static final Type<Beacon> TYPE = new Type<>(RamielMod.id("beacon"));
        public static final StreamCodec<ByteBuf, Beacon> CODEC = StreamCodec.of(Beacon::write, Beacon::read);

        private static void write(ByteBuf buf, Beacon p) {
            buf.writeLong(p.id.getMostSignificantBits()); buf.writeLong(p.id.getLeastSignificantBits());
            buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
            buf.writeFloat(p.aimYaw); buf.writeFloat(p.aimPitch); buf.writeFloat(p.spin);
            buf.writeByte(p.formFrom); buf.writeByte(p.formTo); buf.writeFloat(p.morph);
            buf.writeBoolean(p.alive);
        }

        private static Beacon read(ByteBuf buf) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            return new Beacon(id, buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readByte(), buf.readByte(), buf.readFloat(), buf.readBoolean());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ------------------------------------------------------------------------------------------
    public record SettingsOpen(double[] values, boolean canEdit) implements CustomPacketPayload {
        public static final Type<SettingsOpen> TYPE = new Type<>(RamielMod.id("settings_open"));
        public static final StreamCodec<ByteBuf, SettingsOpen> CODEC = StreamCodec.of(
                (buf, p) -> { writeValues(buf, p.values); buf.writeBoolean(p.canEdit); },
                buf -> new SettingsOpen(readValues(buf), buf.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SettingsSet(double[] values) implements CustomPacketPayload {
        public static final Type<SettingsSet> TYPE = new Type<>(RamielMod.id("settings_set"));
        public static final StreamCodec<ByteBuf, SettingsSet> CODEC = StreamCodec.of(
                (buf, p) -> writeValues(buf, p.values),
                buf -> new SettingsSet(readValues(buf)));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeValues(ByteBuf buf, double[] v) {
        buf.writeShort(v.length);
        for (double d : v) buf.writeDouble(d);
    }

    private static double[] readValues(ByteBuf buf) {
        double[] v = new double[buf.readShort()];
        for (int i = 0; i < v.length; i++) v[i] = buf.readDouble();
        return v;
    }

    // ------------------------------------------------------------------------------------------
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(Fx.TYPE, Fx.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientNet.fx(p)));
        registrar.playToClient(Film.TYPE, Film.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientNet.film(p)));
        registrar.playToClient(Beacon.TYPE, Beacon.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientNet.beacon(p)));
        registrar.playToClient(SettingsOpen.TYPE, SettingsOpen.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientNet.openSettings(p)));
        registrar.playToServer(SettingsSet.TYPE, SettingsSet.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && mayEdit(player)) {
                Setting.apply(p.values());
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("ramiel.settings.saved"));
            }
        }));
    }

    /** Only operators (or the owner of a single-player world) may change settings. */
    public static boolean mayEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    // ------------------------------------------------------------------------------------------
    // Convenience senders used by the server-side code
    // ------------------------------------------------------------------------------------------

    /** Send an effect to every player within {@code range} blocks of {@code at}. */
    public static void fx(ServerLevel level, Vec3 at, double range, Fx payload) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, range, payload);
    }

    public static void fx(ServerLevel level, Vec3 at, double range, int kind, double a, double b, double c, float f1, float f2) {
        fx(level, at, range, new Fx(kind, at.x, at.y, at.z, a, b, c, f1, f2));
    }

    /** Shorthand for the many effects that only need a position and one or two numbers. */
    public static void fx(ServerLevel level, Vec3 at, double range, int kind, double a, double b) {
        fx(level, at, range, kind, a, b, 0, 0f, 0f);
    }

    public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
