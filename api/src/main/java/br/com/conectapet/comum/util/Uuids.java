package br.com.conectapet.comum.util;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Conversao para BINARY(16): 16 bytes em vez dos 36 de um CHAR(36). */
public final class Uuids {

    private Uuids() {}

    public static byte[] paraBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    public static UUID deBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
