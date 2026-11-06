package com.arcadia.arcadiaguard.network;

import io.netty.buffer.ByteBuf;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Codec minimal ecrit a la main (Forge 1.20.1 n'a pas StreamCodec).
 * Accepte n'importe quel ByteBuf et l'enveloppe en FriendlyByteBuf si besoin.
 */
public interface NetCodec<T> {

    void write(FriendlyByteBuf buf, T value);

    T read(FriendlyByteBuf buf);

    default void encode(ByteBuf buf, T value) { write(wrap(buf), value); }

    default T decode(ByteBuf buf) { return read(wrap(buf)); }

    static FriendlyByteBuf wrap(ByteBuf buf) {
        return buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
    }

    static <T> NetCodec<T> of(BiConsumer<FriendlyByteBuf, T> encoder, Function<FriendlyByteBuf, T> decoder) {
        return new NetCodec<>() {
            @Override public void write(FriendlyByteBuf buf, T value) { encoder.accept(buf, value); }
            @Override public T read(FriendlyByteBuf buf) { return decoder.apply(buf); }
        };
    }

    /** Equivalent de ByteBufCodecs.stringUtf8(max) (VarInt longueur + UTF-8, borne). */
    static NetCodec<String> utf8(int maxLength) {
        return of((buf, s) -> buf.writeUtf(s, maxLength), buf -> buf.readUtf(maxLength));
    }
}
