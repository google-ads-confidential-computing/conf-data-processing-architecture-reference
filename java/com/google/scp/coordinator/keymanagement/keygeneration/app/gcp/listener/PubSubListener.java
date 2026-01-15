/*
 * Copyright 2024 Google LLC
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

package com.google.scp.coordinator.keymanagement.keygeneration.app.gcp.listener;

import com.google.api.core.ApiService;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.annotations.VisibleForTesting;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.scp.shared.api.exception.ServiceException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Listens for pubsub message and generates keys when message is received. */
public abstract class PubSubListener {

  private static final Logger logger = LoggerFactory.getLogger(PubSubListener.class);

  private final String projectId;
  private final String subscriptionId;
  private final ReentrantLock lock = new ReentrantLock();

  private TransportChannelProvider channelProvider = null; // Only used for testing
  private CredentialsProvider credentialsProvider = null; // Only used for testing
  private int timeoutInSeconds = -1; // Only used for testing
  private Subscriber subscriber;

  protected PubSubListener(PubSubListenerConfig config, String projectId, String subscriptionId) {
    this.projectId = projectId;
    this.subscriptionId = subscriptionId;
    config.endpointUrl().ifPresent(this::setTransportChannelAndCredentials);
    config.timeoutInSeconds().ifPresent(timeout -> timeoutInSeconds = timeout);
  }

  /** Executes task that will generate keys (if needed) after pubsub message is received. */
  protected abstract void createKeys() throws ServiceException;

  /** Sets up subscriber for given subscriptionId. Creates keys when message is received. */
  public void start() {
    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(projectId, subscriptionId);

    MessageReceiver receiver = getMessageReceiver();
    subscriber = getSubscriber(subscriptionName, receiver);

    // Start the subscriber.
    subscriber.startAsync().awaitRunning();
    logger.info("Listening for messages on SubscriptionId: " + subscriptionId);

    if (timeoutInSeconds != -1) {
      try {
        subscriber.awaitTerminated(timeoutInSeconds, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        logger.info("TimeoutException occurred.", e);
      }
    } else {
      subscriber.awaitTerminated();
    }
  }

  /** Only used for testing */
  @VisibleForTesting
  void stop() {
    if (subscriber != null && subscriber.state() != ApiService.State.TERMINATED) {
      subscriber.stopAsync().awaitTerminated();
    }
  }

  private Subscriber getSubscriber(
      ProjectSubscriptionName subscriptionName, MessageReceiver receiver) {
    Subscriber.Builder builder = Subscriber.newBuilder(subscriptionName, receiver);
    if (channelProvider != null) {
      builder.setChannelProvider(channelProvider);
    }
    if (credentialsProvider != null) {
      builder.setCredentialsProvider(credentialsProvider);
    }
    return builder.build();
  }

  private MessageReceiver getMessageReceiver() {
    return (PubsubMessage message, AckReplyConsumer consumer) -> {
      // Handle incoming message, then ack the received message.
      logger.info(
          "Message Received. Id: "
              + message.getMessageId()
              + "Data: "
              + message.getData().toStringUtf8());
      lock.lock();
      try {
        createKeys();
        logger.info(
            "Task has been invoked. Send ack for message with Id: " + message.getMessageId());
        consumer.ack();
      } catch (ServiceException e) {
        logger.error("Error creating keys.", e);
      } finally {
        lock.unlock();
      }
    };
  }

  private void setTransportChannelAndCredentials(String endpointUrl) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(endpointUrl).usePlaintext().build();
    this.channelProvider =
        FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
    this.credentialsProvider = NoCredentialsProvider.create();
  }
}
