package com.anistal.streamexample.commons.implicits

import akka.actor.ActorSystem
import com.anistal.streamexample.commons.constants.AppConstants
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}

object AppImplicits {

  lazy implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global
  lazy implicit val config: Config = ConfigFactory.load(getClass.getClassLoader,
    ConfigResolveOptions.defaults.setAllowUnresolved(true)).resolve
  lazy implicit val system: ActorSystem = ActorSystem(AppConstants.AkkaClusterName, config)

}
