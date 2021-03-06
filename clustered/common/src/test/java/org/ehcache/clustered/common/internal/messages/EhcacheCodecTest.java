/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehcache.clustered.common.internal.messages;

import org.junit.Test;

import java.util.UUID;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class EhcacheCodecTest {

  private static final UUID CLIENT_ID = UUID.randomUUID();

  @Test
  public void encodeMessage() throws Exception {
    ServerStoreOpCodec serverStoreOpCodec = mock(ServerStoreOpCodec.class);
    LifeCycleMessageCodec lifeCycleMessageCodec = mock(LifeCycleMessageCodec.class);
    StateRepositoryOpCodec stateRepositoryOpCodec = mock(StateRepositoryOpCodec.class);
    ClientIDTrackerMessageCodec clientIDTrackerMessageCodec = mock(ClientIDTrackerMessageCodec.class);
    EhcacheCodec codec = new EhcacheCodec(serverStoreOpCodec, lifeCycleMessageCodec, stateRepositoryOpCodec, null, clientIDTrackerMessageCodec);

    LifecycleMessage.DestroyServerStore lifecycleMessage = new LifecycleMessage.DestroyServerStore("foo", CLIENT_ID);
    codec.encodeMessage(lifecycleMessage);
    verify(lifeCycleMessageCodec, only()).encode(any(LifecycleMessage.class));
    verify(serverStoreOpCodec, never()).encode(any(ServerStoreOpMessage.class));
    verify(stateRepositoryOpCodec, never()).encode(any(StateRepositoryOpMessage.class));
    verify(clientIDTrackerMessageCodec, never()).encode(any(ClientIDTrackerMessage.class));

    ServerStoreOpMessage.ClearMessage serverStoreOpMessage = new ServerStoreOpMessage.ClearMessage("foo", CLIENT_ID);
    codec.encodeMessage(serverStoreOpMessage);
    verify(lifeCycleMessageCodec, only()).encode(any(LifecycleMessage.class));
    verify(serverStoreOpCodec, only()).encode(any(ServerStoreOpMessage.class));
    verify(stateRepositoryOpCodec, never()).encode(any(StateRepositoryOpMessage.class));
    verify(clientIDTrackerMessageCodec, never()).encode(any(ClientIDTrackerMessage.class));

    StateRepositoryOpMessage.EntrySetMessage stateRepositoryOpMessage = new StateRepositoryOpMessage.EntrySetMessage("foo", "bar", CLIENT_ID);
    codec.encodeMessage(stateRepositoryOpMessage);
    verify(lifeCycleMessageCodec, only()).encode(any(LifecycleMessage.class));
    verify(serverStoreOpCodec, only()).encode(any(ServerStoreOpMessage.class));
    verify(stateRepositoryOpCodec, only()).encode(any(StateRepositoryOpMessage.class));
    verify(clientIDTrackerMessageCodec, never()).encode(any(ClientIDTrackerMessage.class));

    ClientIDTrackerMessage clientIDTrackerMessage = new ClientIDTrackerMessage(20L, CLIENT_ID);
    codec.encodeMessage(clientIDTrackerMessage);
    verify(lifeCycleMessageCodec, only()).encode(any(LifecycleMessage.class));
    verify(serverStoreOpCodec, only()).encode(any(ServerStoreOpMessage.class));
    verify(stateRepositoryOpCodec, only()).encode(any(StateRepositoryOpMessage.class));
    verify(clientIDTrackerMessageCodec, only()).encode(any(ClientIDTrackerMessage.class));

  }

  @Test
  public void decodeMessage() throws Exception {
    ServerStoreOpCodec serverStoreOpCodec = mock(ServerStoreOpCodec.class);
    LifeCycleMessageCodec lifeCycleMessageCodec = mock(LifeCycleMessageCodec.class);
    StateRepositoryOpCodec stateRepositoryOpCodec = mock(StateRepositoryOpCodec.class);
    ClientIDTrackerMessageCodec clientIDTrackerMessageCodec = mock(ClientIDTrackerMessageCodec.class);
    EhcacheCodec codec = new EhcacheCodec(serverStoreOpCodec, lifeCycleMessageCodec, stateRepositoryOpCodec, null, clientIDTrackerMessageCodec);

    byte[] payload = new byte[1];

    for (byte i = 1; i <= EhcacheEntityMessage.Type.LIFECYCLE_OP.getCode(); i++) {
      payload[0] = i;
      codec.decodeMessage(payload);
    }
    verify(lifeCycleMessageCodec, times(10)).decode(payload);
    verify(serverStoreOpCodec, never()).decode(payload);
    verify(stateRepositoryOpCodec, never()).decode(payload);
    verify(clientIDTrackerMessageCodec, never()).decode(payload);

    for (byte i = 11; i <= EhcacheEntityMessage.Type.SERVER_STORE_OP.getCode(); i++) {
      payload[0] = i;
      codec.decodeMessage(payload);
    }
    verify(lifeCycleMessageCodec, times(10)).decode(payload);
    verify(serverStoreOpCodec, times(10)).decode(payload);
    verify(stateRepositoryOpCodec, never()).decode(payload);
    verify(clientIDTrackerMessageCodec, never()).decode(payload);

    for (byte i = 21; i <= EhcacheEntityMessage.Type.STATE_REPO_OP.getCode(); i++) {
      payload[0] = i;
      codec.decodeMessage(payload);
    }
    verify(lifeCycleMessageCodec, times(10)).decode(payload);
    verify(serverStoreOpCodec, times(10)).decode(payload);
    verify(stateRepositoryOpCodec, times(10)).decode(payload);
    verify(clientIDTrackerMessageCodec, never()).decode(payload);

    for (byte i = 31; i <= EhcacheEntityMessage.Type.REPLICATION_OP.getCode(); i++) {
      payload[0] = i;
      codec.decodeMessage(payload);
    }
    verify(lifeCycleMessageCodec, times(10)).decode(payload);
    verify(serverStoreOpCodec, times(10)).decode(payload);
    verify(stateRepositoryOpCodec, times(10)).decode(payload);
    verify(clientIDTrackerMessageCodec, times(10)).decode(payload);

  }
}