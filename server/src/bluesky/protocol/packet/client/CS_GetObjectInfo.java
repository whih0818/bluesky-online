package bluesky.protocol.packet.client;

import bluesky.protocol.packet.Packet;
import org.msgpack.annotation.Message;

@Message
public class CS_GetObjectInfo implements Packet {
    public int map_id;
    public long object_id;

    @Override
    public byte getPacketId() { return 7; }

    public CS_GetObjectInfo() {}
    public CS_GetObjectInfo(int map_id, long object_id) {
        this.map_id = map_id;
        this.object_id = object_id;
    }
}
