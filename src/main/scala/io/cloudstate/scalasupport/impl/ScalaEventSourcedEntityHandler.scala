package io.cloudstate.scalasupport.impl

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Props}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import io.cloudstate.protocol.entity.{Failure, Command => EntityCommand}
import io.cloudstate.protocol.event_sourced.{EventSourced, EventSourcedStreamIn, EventSourcedStreamOut}
import io.cloudstate.scalasupport.Command
import io.cloudstate.scalasupport.eventsourced.CloudstateEventSourcedBehavior
import scalapb.GeneratedMessage

import scala.util.control.NonFatal

class EventSourcedImpl[Cmd <: GeneratedMessage, Event, State](entities: Map[String, CloudstateEventSourcedBehavior[Cmd, Event, State]],
  context: ActorContext[_], anySupport: AnySupport)(implicit mat: Materializer) extends EventSourced {
  override def handle(in: Source[EventSourcedStreamIn, NotUsed]): Source[EventSourcedStreamOut, NotUsed] = {

    val out = ActorSource.actorRef[EventSourcedStreamOut.Message](
      { case EventSourcedStreamOut.Message.Empty => ()},
      PartialFunction.empty, 8, OverflowStrategy.fail)
      .mapMaterializedValue { out =>
        val sink = ActorSink.actorRef(context.spawnAnonymous(awaitingInit(out), Props()), EventSourcedSignal.Complete, EventSourcedSignal.Failure)
        in.runWith(sink)
      }

    out.map(EventSourcedStreamOut(_))
  }


  private def awaitingInit(out: ActorRef[EventSourcedStreamOut.Message]): Behavior[EventSourcedSignal] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case EventSourcedSignal.In(EventSourcedStreamIn.Message.Init(init)) =>
          entities.get(init.serviceName) match {
            case Some(behavior) =>
              val state = init.snapshot match {
                case None =>
                  EntityState(init.serviceName, init.entityId, 0l, behavior.emptyState, behavior, out, ctx)
                case Some(snapshot) =>
                  val snapshotState = anySupport.decode(snapshot.snapshot.get).asInstanceOf[State]
                  EntityState(init.serviceName, init.entityId, snapshot.snapshotSequence, snapshotState, behavior, out, ctx)
              }

              state.recoveringJournal
            case None =>
              fatalError(ctx, out, s"Received init message for unknown service ${init.serviceName}")
          }

        case EventSourcedSignal.Complete =>
          Behaviors.stopped

          // todo failure

        case other =>
          fatalError(ctx, out, s"Expected init message as first message but got ${other.getClass}")
      }
    }
  }


  private case class EntityState(serviceName: String, entityId: String, sequence: Long, state: State,
    behavior: CloudstateEventSourcedBehavior[Cmd, Event, State], out: ActorRef[EventSourcedStreamOut.Message], ctx: ActorContext[_]) {

    def recoveringJournal: Behavior[EventSourcedSignal] = Behaviors.receiveMessagePartial {
      case EventSourcedSignal.In(EventSourcedStreamIn.Message.Event(event)) =>
        invoke {
          val decodedEvent = anySupport.decode(event.payload.get).asInstanceOf[Event]
          val state = behavior.eventHandler(state, decodedEvent)
          copy(sequence = event.sequence, state = state).recoveringJournal
        }
      case EventSourcedSignal.In(EventSourcedStreamIn.Message.Command(command)) =>
        handleCommand(command)

    }


    private def awaitingCommand(entityId: String, sequence: Long, state: State): Behavior[EventSourcedSignal] = {

    }


    private def awaitingReply(entityId: String, sequence: Long, state: State, commandId: Long): Behavior[EventSourcedSignal] = {

    }

    private def handleCommand(command: EntityCommand): Behavior[EventSourcedSignal] = {
      invoke {
        val decodedCommand = anySupport.decode(command.payload.get)
        val adapted = ctx.messageAdapter[Any](EventSourcedSignal.Reply.apply)
        val cmd = new Command[Any, Any] {
          override def name: String = command.name
          override def message: Any = decodedCommand
          override def replyTo: ActorRef[Any] = adapted
          override def commandId: Long = command.id
        }
        val effect = behavior.commandHandler(state, cmd)


      }
    }

    private def invoke(block: => Behavior[EventSourcedSignal]): Behavior[EventSourcedSignal] = {
      try {
        block
      } catch {
        case NonFatal(e) =>
          val msg = s"Error occurred in entity $entityId of service $serviceName with sequence $sequence"
          ctx.log.error(s"Error occurred in entity $entityId of service $serviceName with sequence $sequence", e)
          out.tell(EventSourcedStreamOut.Message.Failure(Failure(0, msg + ": " + e.getMessage)))
          out.tell(EventSourcedStreamOut.Message.Empty)
          Behaviors.stopped
      }
    }

  }


  private def fatalError(ctx: ActorContext[_], out: ActorRef[EventSourcedStreamOut.Message], msg: String): Behavior[EventSourcedSignal] = {
    ctx.log.error(s"Fatal error in event sourced entity stream: $msg")
    out.tell(EventSourcedStreamOut.Message.Failure(Failure(0, msg)))
    out.tell(EventSourcedStreamOut.Message.Empty)
    Behaviors.stopped
  }
}

private sealed trait EventSourcedSignal
private object EventSourcedSignal {
  case class In(message: EventSourcedStreamIn.Message) extends EventSourcedSignal
  case class Reply(msg: Any)
  case object Complete extends EventSourcedSignal
  case class Failure(f: Throwable) extends EventSourcedSignal
}
