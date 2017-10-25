/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.scheduler.handler

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.ScheduleConvergeHandlerProperties
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.IntentStatus
import com.netflix.spinnaker.keel.scheduler.ConvergeIntent
import com.netflix.spinnaker.keel.scheduler.ScheduleConvergence
import com.netflix.spinnaker.q.MessageHandler
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ScheduleConvergeHandler
@Autowired constructor(
  override val queue: Queue,
  private val properties: ScheduleConvergeHandlerProperties,
  private val intentRepository: IntentRepository,
  private val registry: Registry
) : MessageHandler<ScheduleConvergence> {

  private val log = LoggerFactory.getLogger(javaClass)

  private val invocations = registry.createId("scheduler.invocations", listOf(BasicTag("type", "convergence")))

  override fun handle(message: ScheduleConvergence) {
    log.info("Scheduling intent convergence work")
    registry.counter(invocations).increment()

    try {
      intentRepository.getIntents(statuses = listOf(IntentStatus.ACTIVE))
        .also { log.info("Scheduling ${it.size}} active intents") }
        .forEach {
          queue.push(ConvergeIntent(it, properties.stalenessTtl, properties.timeoutTtl))
        }
    } finally {
      queue.reschedule(message, Duration.ofMillis(properties.rescheduleTtl))
    }
  }

  override val messageType = ScheduleConvergence::class.java
}
