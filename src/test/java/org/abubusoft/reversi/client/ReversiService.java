package org.abubusoft.reversi.client;

import com.tinder.scarlet.WebSocket;
import com.tinder.scarlet.ws.Receive;
import com.tinder.scarlet.ws.Send;
import io.reactivex.Flowable;
import org.abubusoft.reversi.server.messages.Greeting;

public interface ReversiService {
  @Receive
  Flowable<WebSocket.Event> observeWebSocketEvent();

  @Send
  void sendGreetings(Greeting greeting);

}
