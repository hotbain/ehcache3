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

package org.ehcache.clustered.client;

import org.ehcache.clustered.client.config.ClusteredResourcePool;
import org.ehcache.clustered.client.config.ClusteringServiceConfiguration;
import org.ehcache.clustered.client.config.builders.ClusteredResourcePoolBuilder;
import org.ehcache.clustered.client.config.builders.ClusteringServiceConfigurationBuilder;
import org.ehcache.clustered.client.internal.EhcacheClientEntity;
import org.ehcache.clustered.client.internal.EhcacheClientEntityService;
import org.ehcache.clustered.client.internal.UnitTestConnectionService;
import org.ehcache.clustered.client.internal.lock.VoltronReadWriteLockEntityClientService;
import org.ehcache.clustered.client.internal.service.ClusteredTierManagerValidationException;
import org.ehcache.clustered.client.internal.service.ClusteringServiceFactory;
import org.ehcache.clustered.client.service.ClusteringService;
import org.ehcache.clustered.common.Consistency;
import org.ehcache.clustered.common.internal.ServerStoreConfiguration;
import org.ehcache.clustered.common.internal.exceptions.LifecycleException;
import org.ehcache.clustered.lock.server.VoltronReadWriteLockServerEntityService;
import org.ehcache.clustered.server.EhcacheServerEntityService;
import org.ehcache.impl.serialization.CompactJavaSerializer;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.terracotta.offheapresource.OffHeapResourcesConfiguration;
import org.terracotta.offheapresource.OffHeapResourcesProvider;
import org.terracotta.offheapresource.config.MemoryUnit;
import org.terracotta.passthrough.PassthroughClusterControl;
import org.terracotta.passthrough.PassthroughServer;
import org.terracotta.passthrough.PassthroughTestHelpers;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static org.ehcache.clustered.client.internal.UnitTestConnectionService.getOffheapResourcesType;
import static org.ehcache.config.units.MemoryUnit.MB;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ActivePassiveSyncTest {

  private PassthroughClusterControl clusterControl;
  private static String STRIPENAME = "stripe";
  private static String STRIPE_URI = "passthrough://" + STRIPENAME;

  @Before
  public void setUp() throws Exception {
    this.clusterControl = PassthroughTestHelpers.createActivePassive(STRIPENAME,
      new PassthroughTestHelpers.ServerInitializer() {
        @Override
        public void registerServicesForServer(PassthroughServer server) {
          server.registerServerEntityService(new EhcacheServerEntityService());
          server.registerClientEntityService(new EhcacheClientEntityService());
          server.registerServerEntityService(new VoltronReadWriteLockServerEntityService());
          server.registerClientEntityService(new VoltronReadWriteLockEntityClientService());
          server.registerServiceProvider(new OffHeapResourcesProvider(),
            new OffHeapResourcesConfiguration(getOffheapResourcesType("test", 32, MemoryUnit.MB)));

          UnitTestConnectionService.addServerToStripe(STRIPENAME, server);
        }
      }
    );

    clusterControl.waitForActive();
    clusterControl.waitForRunningPassivesInStandby();
  }

  @After
  public void tearDown() throws Exception {
    UnitTestConnectionService.removeStripe(STRIPENAME);
    clusterControl.tearDown();
  }

  @Test
  public void testTierManagerStateSync() throws Exception {
    clusterControl.terminateOnePassive();

    ClusteringServiceConfiguration configuration =
      ClusteringServiceConfigurationBuilder.cluster(URI.create(STRIPE_URI))
        .autoCreate()
        .defaultServerResource("test")
        .resourcePool("foo", 8L, MB)
        .build();

    ClusteringService service = new ClusteringServiceFactory().create(configuration);
    service.start(null);
    EhcacheClientEntity clientEntity = getEntity(service);

    clusterControl.startOneServer();
    clusterControl.waitForRunningPassivesInStandby();
    clusterControl.terminateActive();

    try {
      clientEntity.validate(configuration.getServerConfiguration());
      fail("ClusteredTierManagerValidationException Expected.");
    } catch (ClusteredTierManagerValidationException e) {
      assertThat(e.getCause(), instanceOf(LifecycleException.class));
      assertThat(e.getCause().getMessage(), containsString("is already being tracked with Client Id"));
    }
    service.stop();
  }

  @Test
  public void testServerStoreStateSync() throws Exception {
    clusterControl.terminateOnePassive();

    ClusteringServiceConfiguration configuration =
      ClusteringServiceConfigurationBuilder.cluster(URI.create(STRIPE_URI))
        .autoCreate()
        .defaultServerResource("test")
        .build();

    ClusteringService service = new ClusteringServiceFactory().create(configuration);
    service.start(null);
    EhcacheClientEntity clientEntity = getEntity(service);
    clientEntity.createCache("foo", getServerStoreConfiguration("test"));

    clusterControl.startOneServer();
    clusterControl.waitForRunningPassivesInStandby();
    clusterControl.terminateActive();

    clientEntity.validateCache("foo", getServerStoreConfiguration("test"));

    service.stop();
  }

  private static EhcacheClientEntity getEntity(ClusteringService clusteringService) throws NoSuchFieldException, IllegalAccessException {
    Field entity = clusteringService.getClass().getDeclaredField("entity");
    entity.setAccessible(true);
    return (EhcacheClientEntity)entity.get(clusteringService);
  }

  private static ServerStoreConfiguration getServerStoreConfiguration(String resourceName) {
    ClusteredResourcePool resourcePool = ClusteredResourcePoolBuilder.clusteredDedicated(resourceName, 8, MB);
    return new ServerStoreConfiguration(resourcePool.getPoolAllocation(),
      String.class.getName(), String.class.getName(), null, null, CompactJavaSerializer.class.getName(), CompactJavaSerializer.class
      .getName(), Consistency.STRONG);
  }
}
