public static class MapScene implements Scene {
    public MapScene(SC_MoveMap moveMap) {
        if(Engine.getInstance().getScene() instanceof LoginScene) {
            Engine.getInstance().getUIManager().clearComponentList();
        }

        Engine.getInstance().playBGM("data/bgm/map_1.mp3");

        //서버로 맵 정보 요청
        Engine.getInstance().getNetwork().write(new CS_GetMapInfo(moveMap.map_id));
    }

    @Override
    public void init() {
    }

    @Override
    public void runSceneLoop() {
    }

    @Override
    public void release() {
    }

    @Override
    public void receivedPacket(Packet packet) {
        print(packet.toString());
    }
}