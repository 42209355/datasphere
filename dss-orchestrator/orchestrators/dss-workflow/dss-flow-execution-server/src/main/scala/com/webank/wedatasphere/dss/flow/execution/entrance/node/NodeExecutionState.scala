/*
 * Copyright 2019 WeBank
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.webank.wedatasphere.dss.flow.execution.entrance.node


object NodeExecutionState extends Enumeration {

  type NodeExecutionState = Value

  val Inited, WaitForRetry, Scheduled, Running, Succeed, Failed, Cancelled, Skipped ,Timeout = Value

  def isRunning(jobState: NodeExecutionState) = Running == jobState

  def isScheduled(jobState: NodeExecutionState) = Scheduled == jobState

  def isInited(jobState: NodeExecutionState) = Inited == jobState

  def isCompleted(jobState: NodeExecutionState) = jobState match {
    case Inited | Scheduled | Running | WaitForRetry => false
    case _ => true
  }

  def isSucceed(jobState: NodeExecutionState) = Succeed == jobState

 def isFailed(jobState: NodeExecutionState):Boolean =  Failed == jobState
}
