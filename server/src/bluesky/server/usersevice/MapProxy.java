package bluesky.server.usersevice;

import bluesky.protocol.packet.client.*;
import bluesky.protocol.packet.service.*;
import bluesky.server.mapservice.DropItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;

public class MapProxy {
    private final static Logger logger = LogManager.getLogger("MapProxy");

    private UserService service;
    private int map_id;
    private short serviceId;

    private LinkedList<UserObject> waitSendMapInfoUser = new LinkedList<UserObject>();
    private LinkedList<UserObject> objects = new LinkedList<UserObject>();

    public MapProxy(UserService service, int map_id) {
        this.service = service;
        this.map_id = map_id;
    }

    public void linkMap() {
        this.service.subscribeMQTT("/maps/" + map_id + "/#");

        try {
            byte[] serviceIdBytes = service.getZooKeeperClient().getData().forPath("/maps/" + map_id).clone();
            short serviceId=0;
            serviceId |= (((int)serviceIdBytes[0])<<8)&0xFF00;
            serviceId |= (((int)serviceIdBytes[1]))&0xFF;
            this.serviceId = serviceId;
        } catch (Exception e) {
            //맵 서버를 발견하지 못할 경우 맵 서버 할당 요청
            this.service.publishMQTT("/event/request_map", new byte[]{
                    (byte)((this.map_id & 0xFF000000) >> 24),
                    (byte)((this.map_id & 0xFF0000) >> 16),
                    (byte)((this.map_id & 0xFF00) >> 8),
                    (byte)((this.map_id & 0xFF))
            });
        }
    }

    public void joinUser(UserObject user) {
        this.objects.add(user);
        user.setMap(this);

        this.service.publishMQTT("/maps/" + getMapId() + "/join", new byte[]{
                (byte)((this.service.getServiceId() & 0xFF00) >> 8),
                (byte)(this.service.getServiceId() & 0xFF),
                (byte)(user.getUUID() >>> 56),
                (byte)(user.getUUID() >>> 48),
                (byte)(user.getUUID() >>> 40),
                (byte)(user.getUUID() >>> 32),
                (byte)(user.getUUID() >>> 24),
                (byte)(user.getUUID() >>> 16),
                (byte)(user.getUUID() >>> 8),
                (byte)(user.getUUID() >>> 0),
                (byte)((user.getX() & 0xFF000000) >> 24),
                (byte)((user.getX() & 0xFF0000) >> 16),
                (byte)((user.getX() & 0xFF00) >> 8),
                (byte)((user.getX() & 0xFF)),
                (byte)((user.getY() & 0xFF000000) >> 24),
                (byte)((user.getY() & 0xFF0000) >> 16),
                (byte)((user.getY() & 0xFF00) >> 8),
                (byte)((user.getY() & 0xFF))
        });

        for(UserObject u : this.objects) {
            if(u == user) continue;

            u.getChannel().write(new MoveObject(user.getUUID(),
                    user.getMapId(), user.getX(), user.getY(), user.getMapId(), user.getX(), user.getY()));
        }
    }

    public void exitUser(UserObject user) {
        this.objects.remove(user);

        //TODO : 안쓰는 맵프록시 제거 개발
    }

    public void linkService(short serviceId) {
        if(this.serviceId == serviceId) return;
        this.serviceId = serviceId;

        for(UserObject user : this.waitSendMapInfoUser) {
            this.sendMapInfo(user);
        }
        this.waitSendMapInfoUser.clear();
    }

    public int getMapId() {
        return this.map_id;
    }

    public void sendMapInfo(UserObject user) {
        if(this.serviceId == 0) {
            //아직 연결 못함
            waitSendMapInfoUser.add(user);
            return;
        }
        this.service.sendServiceMessage(this.serviceId, new GetMapInfo(user.getUUID(), user.getMapId(), getMapId()));
    }

    public void arrivedMQTTMessage(String subTopic, byte[] data) {
        if(subTopic.equals("/join")) {
        }

        if(subTopic.equals("/link_around_map")) {
            for(UserObject user : this.objects) {
                this.sendMapInfo(user);
            }
        }

        if(subTopic.equals("/drop_item")) {
            long uuid = 0;
            int mapId=0;
            int x=0;
            int y=0;
            byte resId=0;

            uuid |= data[0] << 56;
            uuid |= data[1] << 48;
            uuid |= data[2] << 40;
            uuid |= data[3] << 32;
            uuid |= data[4] << 24;
            uuid |= data[5] << 16;
            uuid |= data[6] << 8;
            uuid |= data[7];
            mapId |= data[8] << 24;
            mapId |= data[9] << 16;
            mapId |= data[10] << 8;
            mapId |= data[11];
            x = data[12];
            y = data[13];
            resId = data[14];

            SC_DropItem dropItem = new SC_DropItem(uuid, mapId, x, y, resId);
            for(UserObject user : this.objects) {
                user.getChannel().write(dropItem);
            }
        }

        if(subTopic.equals("/pickup_item")) {
            long uuid = 0;
            int mapId=0;

            uuid |= data[0] << 56;
            uuid |= data[1] << 48;
            uuid |= data[2] << 40;
            uuid |= data[3] << 32;
            uuid |= data[4] << 24;
            uuid |= data[5] << 16;
            uuid |= data[6] << 8;
            uuid |= data[7];
            mapId |= data[8] << 24;
            mapId |= data[9] << 16;
            mapId |= data[10] << 8;
            mapId |= data[11];

            SC_RemoveDropItem removeDropItem = new SC_RemoveDropItem(uuid, mapId);
            for(UserObject user : this.objects) {
                user.getChannel().write(removeDropItem);
            }
        }

        if(subTopic.equals("/set_tile")) {
            int mapId=0;
            int x=0;
            int y=0;
            byte tile=0;

            mapId |= data[0] << 24;
            mapId |= data[1] << 16;
            mapId |= data[2] << 8;
            mapId |= data[3];
            x |= data[4];
            y |= data[5];
            tile = data[6];

            SetTile setTile = new SetTile(mapId, x, y, tile);
            for(UserObject user : this.objects) {
                user.getChannel().write(setTile);
            }
        }
    }

    public void moveObject(UserObject user, MoveObject packet) {
        for(UserObject u : this.objects) {
            if(u == user) continue;
            u.getChannel().write(packet);
        }

        user.setX(packet.dest_x);
        user.setY(packet.dest_y);
        if(user.getMapId() == packet.dest_map) return;
        if(packet.dest_map == getMapId()) {
            this.joinUser(user);
        } else {
            this.exitUser(user);
        }
    }

    public void responseMapInfo(MapInfo info) {
        UserObject requestUser = null;

        for(UserObject user : this.objects) {
            if(user.getUUID() != info.request_id) continue;
            requestUser = user;
            break;
        }

        if(requestUser == null) return;

        SC_MapInfo mapInfo = new SC_MapInfo();
        mapInfo.map_id = info.map_id;
        mapInfo.around_map_id = info.around_map_id;
        mapInfo.tiles = info.tiles;
        requestUser.getChannel().write(mapInfo);

        //오브젝트들 전송
        for(UserObject user : this.objects) {
            requestUser.getChannel().write(new MoveObject(user.getUUID(),
                    user.getMapId(), user.getX(), user.getY(), user.getMapId(), user.getX(), user.getY()));
        }

        for(DropItem item : info.dropitems) {
            requestUser.getChannel().write(new SC_DropItem(item.uuid, this.getMapId(),
                    item.getX(), item.getY(), item.getResId()));
        }
    }

    public void getObjectInfo(UserObject user, long object_id) {
        for(UserObject u : this.objects) {
            if(u.getUUID() != object_id) continue;
            user.getChannel().write(new SC_ObjectInfo(this.getMapId(), u.getUUID(), u.getName()));
            break;
        }
    }

    public void sendChat(Chat chat) {
        for(UserObject u : this.objects) {
            if(u.getUUID() == chat.object_id) continue;
            u.getChannel().write(chat);
            break;
        }
    }

    public void breakTile(UserObject user, int x, int y) {
        this.service.sendServiceMessage(this.serviceId, new ServiceBreakTile(getMapId(), x, y));
    }

    public void pickUpItem(UserObject user, long objectId) {
        this.service.sendServiceMessage(this.serviceId, new ServicePickUpItem(objectId, getMapId()));
    }

    public void setTile(UserObject user, int x, int y, byte resId) {
        this.service.sendServiceMessage(this.serviceId, new ServiceSetTile(getMapId(), x, y, resId));
    }
}
