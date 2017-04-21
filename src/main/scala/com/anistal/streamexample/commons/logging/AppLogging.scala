package com.anistal.streamexample.commons.logging

import org.slf4j.LoggerFactory

/**
 * All objects that needs to log should extend this class.
 */
trait AppLogging {

  protected[this] val log = LoggerFactory.getLogger(getClass.getName)
}
