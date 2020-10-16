package communicationResources;

import javax.websocket.*;

@ClientEndpoint
public class WebSocketClient {

    @OnOpen
    public void connect(Session session){
        ServerConnection.getInstance().onServerConnect(session);
    }

    @OnMessage
    public void newMessage(String msg){
        ServerConnection.getInstance().newCommandFromServer(msg);
    }

    @OnClose
    public void disconnect(Session session, CloseReason reason){
        ServerConnection.getInstance().onServerClose();
    }

}
