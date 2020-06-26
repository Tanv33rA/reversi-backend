package org.abubusoft.reversi.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.fmt.games.reversi.model.Piece;

import java.util.UUID;

public class MatchEndMessage extends MatchMessage {

  @JsonCreator
  public MatchEndMessage(@JsonProperty("playerId") UUID playerId, @JsonProperty("matchId") UUID matchId) {
    super(playerId, matchId, MatchMessageType.MATCH_END);
  }
}