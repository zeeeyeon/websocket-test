package practice.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import practice.websocket.entity.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    // 예를 들어, 채팅방에 이미 접속 해 있던 유저들에게 신규 유저가 들어온 것을 알려줘야 함
    // 그러면 채팅방에 접속 해 있던 기존 접속 사용자의 웹소켓 세션을 전부 관리하고 있어야 함
    // <세션 Id => String, 세션 => WebSocketSession> key - value 형식으로 저장
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 웹소켓 연결
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);

        // 입장하였을 때, 보낼 메세지
        Message message = Message.builder()
                .sender(sessionId)
                .receiver("all")
                .build();
        message.newConnect();

        String jsonMessage = objectMapper.writeValueAsString(message);

        sessions.values().forEach(s -> {
            try {
                if (!s.getId().equals(sessionId)) {
                    s.sendMessage(new TextMessage(jsonMessage));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // 양방향 데이터 통신
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        // client 가 보낸 json 문자열 메세지
        Message message = objectMapper.readValue(textMessage.getPayload(), Message.class);
        message.setSender(session.getId());

        WebSocketSession receiver = sessions.get(session.getId());

        if (receiver != null && session.isOpen()) {
            receiver.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }

    // 소켓 연결 종료
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        // 세션 저장소에서 연결이 끊긴 유저 삭제
        sessions.remove(sessionId);

        // 종료 메세지 생성
        final Message message = new Message();
        message.closeConnect();
        message.setSender(session.getId());

        // 남은 유저에게 메세지 전송
        sessions.values().forEach(s -> {
            try {
                s.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // 소켓 통신 에러
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {}
}
