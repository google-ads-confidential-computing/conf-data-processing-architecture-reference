/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.scp.operator.frontend.service.gcp.testing;

import static com.google.scp.operator.shared.dao.jobqueue.gcp.PubSubJobQueueTestModule.PROJECT_ID;
import static com.google.scp.operator.shared.dao.jobqueue.gcp.PubSubJobQueueTestModule.SUBSCRIPTION_ID;
import static com.google.scp.operator.shared.dao.jobqueue.gcp.PubSubJobQueueTestModule.TOPIC_ID;

import com.google.cloud.pubsub.v1.stub.PublisherStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.TopicName;
import com.google.scp.operator.shared.dao.jobqueue.common.JobQueue.JobQueueMessageLeaseSeconds;
import com.google.scp.operator.shared.dao.jobqueue.gcp.PubSubJobQueue.JobQueuePubSubSubscriptionName;
import com.google.scp.operator.shared.dao.jobqueue.gcp.PubSubJobQueue.JobQueuePubSubTopicName;
import com.google.scp.operator.shared.dao.jobqueue.gcp.PubSubJobQueue.JobQueuePublisherStub;
import com.google.scp.operator.shared.dao.jobqueue.gcp.PubSubJobQueueTestModule;
import com.google.scp.operator.shared.dao.metadatadb.gcp.SpannerMetadataDb.MetadataDbSpannerTtlDays;
import com.google.scp.shared.clients.configclient.ParameterClient;
import com.google.scp.shared.clients.configclient.local.LocalParameterClient;
import com.google.scp.shared.testutils.gcp.PubSubEmulatorContainer;

/** Test environment for frontend service integration test */
public class FrontendServiceIntegrationTestModule extends AbstractModule {
  private Injector pubSubInjector;

  @Provides
  @Singleton
  public PubSubEmulatorContainer providePubSubEmulatorContainer() {
    return pubSubInjector.getInstance(PubSubEmulatorContainer.class);
  }

  @Override
  public void configure() {
    pubSubInjector = Guice.createInjector(new PubSubJobQueueTestModule());
    pubSubInjector.getInstance(PubSubEmulatorContainer.class).start();

    bind(PublisherStub.class)
        .annotatedWith(JobQueuePublisherStub.class)
        .toInstance(
            pubSubInjector.getInstance(Key.get(PublisherStub.class, JobQueuePublisherStub.class)));
    bind(SubscriberStub.class).toInstance(pubSubInjector.getInstance(SubscriberStub.class));
    bind(ParameterClient.class).toInstance(new LocalParameterClient(ImmutableMap.of()));
    bind(String.class)
        .annotatedWith(JobQueuePubSubTopicName.class)
        .toInstance(TopicName.format(PROJECT_ID, TOPIC_ID));
    bind(int.class).annotatedWith(JobQueueMessageLeaseSeconds.class).toInstance(10);
    bind(String.class)
        .annotatedWith(JobQueuePubSubSubscriptionName.class)
        .toInstance(ProjectSubscriptionName.format(PROJECT_ID, SUBSCRIPTION_ID));
    bind(int.class).annotatedWith(MetadataDbSpannerTtlDays.class).toInstance(1);
  }
}
