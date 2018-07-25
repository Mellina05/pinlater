/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pinterest.pinlater.commons.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.twitter.common.base.Command;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.zookeeper.Group;
import com.twitter.common.zookeeper.ServerSet;
import com.twitter.thrift.Endpoint;
import com.twitter.thrift.ServiceInstance;
import com.twitter.thrift.Status;
import com.twitter.util.ExceptionalFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;

/**
 * Implementation of the ServerSet interface that uses a file on local disk instead of talking
 * to ZooKeeper directly. The file is typically downloaded by an external daemon process that
 * registers a watch on the actual ZooKeeper server set. Consumers of this class simply need to
 * provide the ZooKeeper server set path. All other details are handled automatically, since the
 * external daemon is configured to use a standard and consistent file path scheme.
 *
 * Note that this implementation only supports monitor() and not join(). Use the standard
 * ZooKeeper implementation for join().
 *
 */
public class ConfigFileServerSet implements ServerSet {

  private static Logger LOG = LoggerFactory.getLogger(ConfigFileServerSet.class);

  protected final ConfigFileWatcher configFileWatcher;
  protected final String serverSetFilePath;

  /**
   * Create a ConfigFileServerSet instance.
   *
   * @param serverSetZKPath ZooKeeper server set path
   */
  public ConfigFileServerSet(String serverSetZKPath) {
    this(ConfigFileWatcher.defaultInstance(), serverSetZKPath);
  }

  /**
   * Internal constructor. This is provided for use by unit test.
   *
   * @param configFileWatcher ConfigFileWatcher instance to use.
   * @param serverSetFilePath Path the server set file on local disk. This is expected to contain
   *                          a list of host:port pairs, one per line. An external daemon will be
   *                          responsible for keeping this in sync with the actual server set in
   *                          ZooKeeper.
   */
  @VisibleForTesting
  ConfigFileServerSet(ConfigFileWatcher configFileWatcher, String serverSetFilePath) {
    this.serverSetFilePath = MorePreconditions.checkNotBlank(serverSetFilePath);
    this.configFileWatcher = Preconditions.checkNotNull(configFileWatcher);

    File file = new File(serverSetFilePath);
    if (!file.exists()) {
      String message = String.format("Server set file: %s doesn't exist", serverSetFilePath);
      throw new IllegalArgumentException(message);
    }
  }

  public EndpointStatus join(
      InetSocketAddress endpoint, Map<String, InetSocketAddress> additionalEndpoints, Status status)
      throws Group.JoinException, InterruptedException {
    throw new UnsupportedOperationException("ConfigFileServerSet does not support join()");
  }

  public EndpointStatus join(
      InetSocketAddress endpoint, Map<String, InetSocketAddress> additionalEndpoints)
      throws Group.JoinException, InterruptedException {
    throw new UnsupportedOperationException("ConfigFileServerSet does not support join()");
  }

  public EndpointStatus join(
      InetSocketAddress endpoint, Map<String, InetSocketAddress> additionalEndpoints, int shardId)
      throws Group.JoinException, InterruptedException {
    throw new UnsupportedOperationException("ConfigFileServerSet does not support join()");
  }

  public Command watch(final HostChangeMonitor<ServiceInstance> monitor) throws MonitorException {
    monitor(monitor);
    return null;
  }

  public void monitor(final HostChangeMonitor<ServiceInstance> monitor) throws MonitorException {
    Preconditions.checkNotNull(monitor);
    try {
      // Each call to monitor registers a new file watch. This is a bit inefficient if there
      // are many calls to monitor(), since each watch needs to parse the file contents
      // independently. But it is simple and delegates keeping track of a list of monitors to the
      // ConfigFileWatcher. In practice, we don't really expect multiple calls to monitor anyway.
      configFileWatcher.addWatch(serverSetFilePath,
          new ExceptionalFunction<byte[], Void>() {
            @Override
            public Void applyE(byte[] newContents) throws Exception {
              ImmutableSet<ServiceInstance> newServerSet = readServerSet(newContents);
              monitor.onChange(newServerSet);
              return null;
            }
          });
    } catch (IOException e) {
      throw new MonitorException(
          "Error setting up watch on dynamic server set file:" + serverSetFilePath, e);
    }
  }

  protected static ImmutableSet<ServiceInstance> readServerSet(byte[] fileContent)
      throws IOException {
    ImmutableSet.Builder<ServiceInstance> builder = new ImmutableSet.Builder<ServiceInstance>();
    InputStream stream = new ByteArrayInputStream(fileContent);
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    while (true) {
      String line = reader.readLine();
      if (line == null) {
        // EOF.
        break;
      } else if (line.isEmpty()) {
        // Skip empty lines.
        continue;
      }

      // We expect each line to be of the form "hostname:port". Note that host names can
      // contain ':' themselves (e.g. ipv6 addresses).
      int index = line.lastIndexOf(':');
      Preconditions.checkArgument(index > 0 && index < line.length() - 1);

      String host = line.substring(0, index);
      int port = Integer.parseInt(line.substring(index + 1));
      builder.add(new ServiceInstance(
          new Endpoint(host, port),                 // endpoint
          Collections.<String, Endpoint>emptyMap(), // additional endpoints
          Status.ALIVE));                           // status
    }
    return builder.build();
  }
}
