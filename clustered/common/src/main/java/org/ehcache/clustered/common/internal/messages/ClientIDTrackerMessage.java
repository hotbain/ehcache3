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

import org.ehcache.clustered.common.internal.store.Chain;

import java.util.UUID;

/**
 * This message is sent by the Active Entity to Passive Entity.
 */
public class ClientIDTrackerMessage extends EhcacheEntityMessage {

  public enum ReplicationOp {
    CHAIN_REPLICATION_OP((byte) 31),
    CLIENTID_TRACK_OP((byte) 32)
    ;

    private final byte replicationOpCode;

    ReplicationOp(byte replicationOpCode) {
      this.replicationOpCode = replicationOpCode;
    }

    public byte getReplicationOpCode() {
      return replicationOpCode;
    }


    public static ReplicationOp getReplicationOp(byte replicationOpCode) {
      switch (replicationOpCode) {
        case 31:
          return CHAIN_REPLICATION_OP;
        case 32:
          return CLIENTID_TRACK_OP;
        default:
          throw new IllegalArgumentException("Replication operation not defined for : " + replicationOpCode);
      }
    }
  }

  private final UUID clientId;
  private final long msgId;

  public ClientIDTrackerMessage(long msgId, UUID clientId) {
    this.msgId = msgId;
    this.clientId = clientId;
  }

  @Override
  public Type getType() {
    return Type.REPLICATION_OP;
  }

  @Override
  public byte getOpCode() {
    return operation().getReplicationOpCode();
  }

  @Override
  public void setId(long id) {
    throw new UnsupportedOperationException("This method is not supported on replication message");
  }

  public ReplicationOp operation() {
    return ReplicationOp.CLIENTID_TRACK_OP;
  }

  public long getId() {
    return msgId;
  }

  public UUID getClientId() {
    return clientId;
  }

  public static class ChainReplicationMessage extends ClientIDTrackerMessage implements ConcurrentEntityMessage {

    private final String cacheId;
    private final long key;
    private final Chain chain;

    public ChainReplicationMessage(String cacheId, long key, Chain chain, long msgId, UUID clientId) {
      super(msgId, clientId);
      this.cacheId = cacheId;
      this.key = key;
      this.chain = chain;
    }

    public String getCacheId() {
      return this.cacheId;
    }

    public long getKey() {
      return key;
    }

    public Chain getChain() {
      return chain;
    }

    @Override
    public ReplicationOp operation() {
      return ReplicationOp.CHAIN_REPLICATION_OP;
    }

    @Override
    public int concurrencyKey() {
      return (int) (this.cacheId.hashCode() + key);
    }
  }
}
