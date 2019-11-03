/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.huawei.config;

import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.huawei.config.client.ServiceCombConfigClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @Author wangqijun
 * @Date 16:19 2019-10-23
 **/
public class ConfigWatch implements ApplicationEventPublisherAware, SmartLifecycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigWatch.class);

  private final AtomicBoolean ready = new AtomicBoolean(false);

  private final TaskScheduler taskScheduler;

  private ServiceCombConfigProperties serviceCombConfigProperties;

  private ServiceCombConfigClient serviceCombConfigClient;

  private ScheduledFuture<?> watchScheduledFuture;

  private ApplicationEventPublisher applicationEventPublisher;

  private ContextRefresher contextRefresher;


  public ConfigWatch(ServiceCombConfigProperties serviceCombConfigProperties,
      ServiceCombConfigClient serviceCombConfigClient, ContextRefresher contextRefresher) {
    this.serviceCombConfigProperties = serviceCombConfigProperties;
    this.serviceCombConfigClient = serviceCombConfigClient;
    ThreadPoolTaskScheduler threadPool = new ThreadPoolTaskScheduler();
    threadPool.initialize();
    taskScheduler = threadPool;
    this.contextRefresher = contextRefresher;
  }

  @Override
  public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  public void start() {
    if (ready.compareAndSet(false, true)) {
      watchScheduledFuture = taskScheduler.scheduleWithFixedDelay(
          this::watch, serviceCombConfigProperties.getWatch().getDelay());
    }
  }

  private void watch() {
    if (ready.get()) {
      Set<String> changeData = contextRefresher.refresh();
      if (changeData != null && !changeData.isEmpty()) {
        LOGGER.info("config data changed  = {}", changeData);
        applicationEventPublisher.publishEvent(new ConfigRefreshEvent(this, changeData));
      }
    }
  }

  @Override
  public void stop() {
    if (this.ready.compareAndSet(true, false) && this.watchScheduledFuture != null) {
      this.watchScheduledFuture.cancel(true);
    }
  }

  @Override
  public boolean isRunning() {
    return this.ready.get();
  }


  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public void stop(Runnable callback) {
    this.stop();
    callback.run();
  }

  @Override
  public int getPhase() {
    return 0;
  }
}