package com.arcadia.arcadiaguard.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;

public record ZoneDataPayload(List<ClientZoneInfo> zones) {

    public record ClientZoneInfo(
        String name,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        boolean dimensional,
        boolean hasParent
    ) {
        private static final NetCodec<String> NAME_C = NetCodec.utf8(64);

        static final NetCodec<ClientZoneInfo> STREAM_CODEC = NetCodec.of(
            (buf, info) -> {
                NAME_C.encode(buf, info.name);
                buf.writeInt(info.minX); buf.writeInt(info.minY); buf.writeInt(info.minZ);
                buf.writeInt(info.maxX); buf.writeInt(info.maxY); buf.writeInt(info.maxZ);
                buf.writeByte((info.dimensional ? 1 : 0) | (info.hasParent ? 2 : 0));
            },
            buf -> {
                String name = NAME_C.decode(buf);
                int minX = buf.readInt(), minY = buf.readInt(), minZ = buf.readInt();
                int maxX = buf.readInt(), maxY = buf.readInt(), maxZ = buf.readInt();
                byte flags = buf.readByte();
                return new ClientZoneInfo(name, minX, minY, minZ, maxX, maxY, maxZ,
                    (flags & 1) != 0, (flags & 2) != 0);
            }
        );
    }

    public static final NetCodec<ZoneDataPayload> STREAM_CODEC =
        NetCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.zones().size());
                for (ClientZoneInfo info : payload.zones()) {
                    ClientZoneInfo.STREAM_CODEC.encode(buf, info);
                }
            },
            buf -> {
                int size = buf.readInt();
                if (size < 0 || size > 4096)
                    throw new io.netty.handler.codec.DecoderException("ZoneDataPayload: zone count out of range: " + size);
                List<ClientZoneInfo> zones = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    zones.add(ClientZoneInfo.STREAM_CODEC.decode(buf));
                }
                return new ZoneDataPayload(zones);
            }
        );

}
