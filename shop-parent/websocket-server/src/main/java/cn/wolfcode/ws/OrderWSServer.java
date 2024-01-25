package cn.wolfcode.ws;

import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/{token}")
@Component
public class OrderWSServer{
    public static ConcurrentHashMap<String, Session>clients=new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token){
        System.out.println("浏览器和服务器建立连接；"+token);
        clients.put(token,session);
    }
    @OnClose
    public void onClose(@PathParam("token") String token){
        System.out.println("浏览器和服务器断开链接"+token);
        clients.remove(token);
    }
    @OnError
    public void onError(Throwable error){
        error.printStackTrace();
    }
}
