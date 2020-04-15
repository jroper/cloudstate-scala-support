package io.cloudstate.scalasupport

import com.google.protobuf.{Any => ProtoAny, Descriptors}


/**
  * A reference to a call on a service.
  */
trait ServiceCallRef[T] {
  /**
    * The protobuf descriptor for the method.
    *
    * @return The protobuf descriptor for the method.
    */
  def method: Descriptors.MethodDescriptor

  /**
    * Create a call from this reference, using the given message as the message to pass to it when
    * it's invoked.
    *
    * @param message The message to pass to the method.
    * @return A service call that can be used as a forward or effect.
    */
  def createCall(message: T): ServiceCall
}

/** Represents a call to a service, performed either as a forward, or as an effect. */
trait ServiceCall {
  /**
    * The reference to the call.
    *
    * @return The reference to the call.
    */
  def ref: ServiceCallRef[_]

  /**
    * The message to pass to the call when the call is invoked.
    *
    * @return The message to pass to the call, serialized as an { @link Any}.
    */
  def message: ProtoAny
}
