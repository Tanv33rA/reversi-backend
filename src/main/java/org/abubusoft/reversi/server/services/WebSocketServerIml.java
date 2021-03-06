package org.abubusoft.reversi.server.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.abubusoft.reversi.messages.ConnectedUser;
import org.abubusoft.reversi.messages.MatchMessage;
import org.abubusoft.reversi.server.web.controllers.WebPathConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.abubusoft.reversi.server.services.GameServiceImpl.HEADER_TYPE;

@Component
public class WebSocketServerIml implements WebSocketSender {
  private final Logger logger= LoggerFactory.getLogger(WebSocketServerIml.class);
  private final ObjectMapper objectMapper;

  public WebSocketServerIml(@Autowired ObjectMapper objectMapper, @Autowired SimpMessageSendingOperations messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
    this.objectMapper=objectMapper;
  }

  final private SimpMessageSendingOperations messagingTemplate;

  @Override
  public <E extends MatchMessage> void sendMessage(UUID userUUID, E message) {
    GameServiceImpl.buildMessage(userUUID, message, logger, objectMapper, messagingTemplate);

    //messagingTemplate.convertAndSendToUser("/status", connectedUser);
  }
}
