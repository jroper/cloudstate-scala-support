package io.cloudstate.scalasupport.eventsourced

import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import io.cloudstate.scalasupport.Command
import scalapb.GeneratedMessage

case class CloudstateEventSourcedBehavior[Cmd <: GeneratedMessage, Event, State](
  emptyState: State,
  commandHandler: (State, Command[Cmd, _]) => ReplyEffect[Event, State],
  eventHandler: (State, Event) => State

)
