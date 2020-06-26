package org.abubusoft.reversi.server.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.fmt.games.reversi.model.Coordinates;
import it.fmt.games.reversi.model.GameSnapshot;
import it.fmt.games.reversi.model.Piece;
import org.abubusoft.reversi.messages.*;
import org.abubusoft.reversi.server.events.MatchEndEvent;
import org.abubusoft.reversi.server.events.MatchMoveEvent;
import org.abubusoft.reversi.server.events.MatchStartEvent;
import org.abubusoft.reversi.server.events.MatchStatusEvent;
import org.abubusoft.reversi.server.model.*;
import org.abubusoft.reversi.server.repositories.MatchStatusRepository;
import org.abubusoft.reversi.server.repositories.UserRepository;
import org.abubusoft.reversi.server.web.controllers.WebPathConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.data.util.Pair;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

import static org.abubusoft.reversi.server.ReversiServerApplication.MATCH_EXECUTOR;

@Component
public class GameServiceImpl implements GameService {
  public static final String HEADER_TYPE = "type";
  private final static Logger logger = LoggerFactory.getLogger(GameServiceImpl.class);
  private final UserRepository userRepository;
  private final MatchStatusRepository matchStatusRepository;
  private final SimpMessageSendingOperations messagingTemplate;
  private final ObjectProvider<MatchService> gameInstanceProvider;
  private final Map<UUID, BlockingQueue<Pair<Piece, Coordinates>>> matchMovesQueues;
  private final Executor matchExecutor;
  private final ObjectMapper objectMapper;

  public GameServiceImpl(UserRepository userRepository,
                         MatchStatusRepository matchStatusRepository,
                         @Qualifier(MATCH_EXECUTOR) Executor matchExecutor,
                         ObjectProvider<MatchService> gameInstanceProvider,
                         SimpMessageSendingOperations messagingTemplate,
                         ObjectMapper objectMapper) {
    this.userRepository = userRepository;
    this.matchStatusRepository = matchStatusRepository;
    this.matchExecutor = matchExecutor;
    this.messagingTemplate = messagingTemplate;
    this.gameInstanceProvider = gameInstanceProvider;
    this.matchMovesQueues = new ConcurrentHashMap<>();
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void playMatch(NetworkPlayer1 player1, NetworkPlayer2 player2) {
    User user1 = userRepository.findById(player1.getUserId()).orElse(null);
    User user2 = userRepository.findById(player2.getUserId()).orElse(null);

    if (user1 != null && user2 != null) {
      BlockingQueue<Pair<Piece, Coordinates>> moveQueue = new LinkedBlockingQueue<>();
      MatchService instance = gameInstanceProvider.getObject(player1, player2, moveQueue);
      logger.debug("playMatch matchId: {}", instance.getId());
      matchMovesQueues.put(instance.getId(), moveQueue);

      MatchStatus matchStatus = matchStatusRepository.save(MatchStatus.of(instance.getId(), null));

      user1.setStatus(UserStatus.IN_GAME);
      user1.setPiece(Piece.PLAYER_1);
      user1.setMatchStatus(matchStatus);
      user2.setStatus(UserStatus.IN_GAME);
      user2.setPiece(Piece.PLAYER_2);
      user2.setMatchStatus(matchStatus);
      userRepository.save(user1);
      userRepository.save(user2);

      matchExecutor.execute(instance::play);
    }

  }

  @EventListener
  @Transactional
  public void onMatchStart(MatchStartEvent event) {
    logger.debug("onMatchStart matchId: {}", event.getMatchUUID());

    MatchStartMessage startMessageForPlayer1 = new MatchStartMessage(event.getPlayer1UUID(), event.getMatchUUID(), Piece.PLAYER_1);
    MatchStartMessage startMessageForPlayer2 = new MatchStartMessage(event.getPlayer2UUID(), event.getMatchUUID(), Piece.PLAYER_2);
    sendToUser(event.getPlayer1UUID(), startMessageForPlayer1);
    sendToUser(event.getPlayer2UUID(), startMessageForPlayer2);
  }

  @EventListener
  @Transactional
  public void onMatchEnd(MatchEndEvent event) {
    logger.debug("onMatchEnd matchId: {}", event.getMatchUUID());

    // remove message queue
    this.matchMovesQueues.remove(event.getMatchUUID());

    MatchStatus matchStatus = matchStatusRepository.findById(event.getMatchUUID()).orElse(null);

    sendToUser(event.getPlayer1UUID(), new MatchEndMessage(event.getPlayer1UUID(), matchStatus.getId(), event.getStatus(), event.getScore()));
    sendToUser(event.getPlayer2UUID(), new MatchEndMessage(event.getPlayer2UUID(), matchStatus.getId(), event.getStatus(), event.getScore()));

    List<UUID> userIds = Arrays.asList(event.getPlayer1UUID(), event.getPlayer2UUID());
    for (UUID userId : userIds) {
      User player = userRepository.findById(userId).orElse(null);
      player.setStatus(UserStatus.NOT_READY_TO_PLAY);
      player.setMatchStatus(null);
      player.setPiece(null);
      userRepository.save(player);
    }

    matchStatus.setFinishDateTime(LocalDateTime.now());
    matchStatusRepository.save(matchStatus);
  }

  @EventListener
  @Transactional
  public void onMatchMove(MatchMoveEvent event) {
    MatchMove move = event.getMove();
    logger.debug("On match matchId: {}, player {} moves {}", move.getMatchId(), move.getPlayerPiece(), move.getMove());
    if (matchMovesQueues.containsKey(move.getMatchId())) {
      matchMovesQueues.get(move.getMatchId()).add(Pair.of(move.getPlayerPiece(), move.getMove()));
    } else {
      logger.warn("no match found with id={}", move.getMatchId());
    }
  }

  @EventListener
  @Transactional
  public void onMatchStatusChanges(MatchStatusEvent event) {
    logger.debug("onMatchStatusChanges matchId: {}", event.getMatchStatus().getId());

    // update snapshot
    GameSnapshot snapshot = event.getMatchStatus().getSnapshot();
    assert event.getMatchStatus().getId() != null;
    MatchStatus matchStatus = matchStatusRepository
            .findById(event.getMatchStatus().getId())
            .orElse(event.getMatchStatus());
    matchStatus.setSnapshot(snapshot);
    matchStatus = matchStatusRepository.save(matchStatus);

    Piece activePiece = event.getMatchStatus().getSnapshot().getActivePiece();
    boolean firstMove = event.getMatchStatus().getSnapshot().getLastMove() == null;
    if (firstMove || activePiece == Piece.PLAYER_1) {
      sendToUser(event.getPlayer1().getUserId(), new MatchStatusMessage(event.getPlayer1().getUserId(), matchStatus.getId(), matchStatus.getSnapshot()));
    }
    if (firstMove || activePiece == Piece.PLAYER_2) {
      sendToUser(event.getPlayer2().getUserId(), new MatchStatusMessage(event.getPlayer2().getUserId(), matchStatus.getId(), matchStatus.getSnapshot()));
    }
  }

  private void sendToUser(UUID userUUID, MatchMessage message) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(HEADER_TYPE, message.getMessageType());
    String userTopic = WebPathConstants.WS_TOPIC_USER_MATCH_DESTINATION.replace("{uuid}", userUUID.toString());
    try {
      logger.debug("Send message {} to {}: {}", message.getClass().getSimpleName(), userTopic, objectMapper.writeValueAsString(message));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    messagingTemplate.convertAndSend(userTopic, message, headers);
  }

  @Override
  public Iterable<MatchStatus> findAllUMatchStatus() {
    return matchStatusRepository.findAll();
  }

  @Override
  public User saveUser(ConnectedUser connectedUser) {
    User user = new User();
    user.setName(connectedUser.getName());
    user.setStatus(UserStatus.NOT_READY_TO_PLAY);
    return userRepository.save(user);
  }

  @Override
  public Iterable<User> findAllUsers() {
    return userRepository.findAll();
  }

  private User updateUserStatus(UUID userUUID, UserStatus updatedStatus) {
    User user = userRepository.findById(userUUID).orElse(null);
    if (user != null) {
      user.setStatus(updatedStatus);
      userRepository.save(user);
    }
    return user;
  }

  @Override
  public MatchStatus findMatchByUserId(UUID userId) {
    User user = userRepository.findById(userId).orElse(null);

    return user != null ? user.getMatchStatus() : null;
  }

  @Override
  @Transactional
  public User readyToPlay(UUID userUUID) {
    User user = userRepository.findById(userUUID).orElse(null);
    User otherUser = userRepository.findByStatus(UserStatus.AWAITNG_TO_START).stream()
            .filter(other -> !other.getId().equals(userUUID))
            .findFirst().orElse(null);

    if (user != null && user.getStatus() == UserStatus.NOT_READY_TO_PLAY) {
      updateUserStatus(userUUID, UserStatus.AWAITNG_TO_START);

      if (otherUser != null) {
        // already set
        //updateUserStatus(otherUser.getId(), UserStatus.AWAITNG_TO_START);
        logger.info("Start match {} vs {}", user.getId(), otherUser.getId());
        playMatch(new NetworkPlayer1(user.getId()),
                new NetworkPlayer2(otherUser.getId()));
      }
    }

    return user;
  }

  @Override
  public User stopPlaying(UUID userUUID) {
    return updateUserStatus(userUUID, UserStatus.NOT_READY_TO_PLAY);
  }


}
