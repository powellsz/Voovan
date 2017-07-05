package org.voovan.http.websocket;

import org.voovan.http.client.WebSocketHandler;
import org.voovan.http.server.HttpRequest;
import org.voovan.network.IoSession;
import org.voovan.network.exception.SendMessageException;
import org.voovan.tools.log.Logger;

import java.nio.ByteBuffer;

/**
 * 类文字命名
 *
 * @author: helyho
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class WebSocketSession {
    private IoSession socketSession;
    private WebSocketRouter webSocketRouter;

    /**
     * 构造函数
     * @param socketSession Socket 会话
     */
    public WebSocketSession(IoSession socketSession){
        this.socketSession = socketSession;
    }

    /**
     * 构造函数
     * @param socketSession Socket 会话
     * @param webSocketRouter WebSocket 路由处理对象
     */
    public WebSocketSession(IoSession socketSession, WebSocketRouter webSocketRouter){
        this.socketSession = socketSession;
        this.webSocketRouter = webSocketRouter;
    }

    /**
     * 获取 WebSocket 路由处理对象
     * @return WebSocket 路由处理对象
     */
    public WebSocketRouter getWebSocketRouter() {
        return webSocketRouter;
    }

    /**
     * 设置获取WebSocket 路由处理对象
     * @param webSocketRouter WebSocket 路由处理对象
     */
    public void setWebSocketRouter(WebSocketRouter webSocketRouter) {
        this.webSocketRouter = webSocketRouter;
    }

    /**
     * 发送 websocket 消息
     * @param byteBuffer 消息内容
     */
    public synchronized void send(ByteBuffer byteBuffer) {


        WebSocketFrame webSocketFrame = WebSocketFrame.newInstance(true, WebSocketFrame.Opcode.TEXT, false, byteBuffer);

        //这里不用syncSend 方法是因为出发 onSent 是异步的,会导致消息顺序错乱
        this.socketSession.send(webSocketFrame.toByteBuffer());
        byteBuffer.remaining();

        //触发发送事件
        webSocketRouter.onSent(this, byteBuffer);
    }

    /**
     * 发送 websocket 帧
     * @param webSocketFrame 帧
     */
    protected synchronized void send(WebSocketFrame webSocketFrame) throws SendMessageException {
        this.socketSession.syncSend(webSocketFrame);

        if(webSocketFrame.getOpcode() == WebSocketFrame.Opcode.TEXT ||
                webSocketFrame.getOpcode() == WebSocketFrame.Opcode.BINARY) {
            //触发发送事件
            webSocketRouter.onSent(this, webSocketFrame.getFrameData());
        }
    }

    /**
     * 判断连接状态
     * @return true: 连接状态, false: 断开状态
     */
    public boolean isConnected(){
        return socketSession.isConnected();
    }

    /**
     * 直接关闭 Socket 连接
     *      不会发送 CLOSING 给客户端
     */
    /**
     * 关闭 WebSocket
     */
    public void close() {
        WebSocketFrame closeWebSocketFrame = WebSocketFrame.newInstance(true, WebSocketFrame.Opcode.CLOSING,
                false, ByteBuffer.wrap(WebSocketTools.intToByteArray(1000, 2)));
        try {
            send(closeWebSocketFrame);
        } catch (SendMessageException e) {
            Logger.error("Close WebSocket error, Socket will be close " ,e);
            socketSession.close();
        }
    }

    protected IoSession getSocketSession() {
        return socketSession;
    }

    protected void setSocketSession(IoSession socketSession) {
        this.socketSession = socketSession;
    }

}
