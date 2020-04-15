package io.cloudstate.scalasupport

import akka.actor.typed.ActorRef

trait Command[Cmd, Reply] {
  def name: String
  def message: Cmd
  def replyTo: ActorRef[Reply]
  def commandId: Long
}

trait CommandMatcher[Cmd, Reply] {
  def unapply(command: Command[_, _]): Option[(Cmd, ActorRef[Reply])]
}

object CommandMatcher {
  def apply[Cmd, Reply](name: String): CommandMatcher[Cmd, Reply] = {
    new CommandMatcher[Cmd, Reply] {
      override def unapply(command: Command[_, _]): Option[(Cmd, ActorRef[Reply])] = {
        if (command.name == name) {
          Some((command.message.asInstanceOf[Cmd], command.replyTo.asInstanceOf[ActorRef[Reply]]))
        } else {
          None
        }
      }
    }
  }
}