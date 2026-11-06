package com.arcadia.arcadiaguard.network.gui;

import com.arcadia.arcadiaguard.network.NetCodec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;

/** S→C : logs d'audit pour une zone, filtrés. */
public record ZoneLogsPayload(String zoneName, List<LogLine> entries) {

    public record LogLine(String timestamp, String player, String action, String pos) {
        private static final NetCodec<String> TS_C     = NetCodec.utf8(32);
        private static final NetCodec<String> PLAYER_C = NetCodec.utf8(16);
        private static final NetCodec<String> ACTION_C = NetCodec.utf8(128);
        private static final NetCodec<String> POS_C    = NetCodec.utf8(48);

        static final NetCodec<LogLine> CODEC = NetCodec.of(
            (buf, l) -> {
                TS_C.encode(buf, l.timestamp);
                PLAYER_C.encode(buf, l.player);
                ACTION_C.encode(buf, l.action);
                POS_C.encode(buf, l.pos);
            },
            buf -> new LogLine(
                TS_C.decode(buf),
                PLAYER_C.decode(buf),
                ACTION_C.decode(buf),
                POS_C.decode(buf)
            )
        );
    }

    private static final NetCodec<String> ZONE_NAME_C = NetCodec.utf8(64);

    public static final NetCodec<ZoneLogsPayload> STREAM_CODEC = NetCodec.of(
        (buf, p) -> {
            ZONE_NAME_C.encode(buf, p.zoneName());
            buf.writeInt(p.entries().size());
            for (LogLine l : p.entries()) LogLine.CODEC.encode(buf, l);
        },
        buf -> {
            String zone = ZONE_NAME_C.decode(buf);
            int n = buf.readInt();
            if (n < 0 || n > 200) throw new io.netty.handler.codec.DecoderException("Invalid size " + n + " in ZoneLogsPayload");
            List<LogLine> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) entries.add(LogLine.CODEC.decode(buf));
            return new ZoneLogsPayload(zone, entries);
        }
    );

}
