package net.ruippeixotog.scalafbp.component.core

import scala.concurrent.duration._

import net.ruippeixotog.scalafbp.component.{ AutoTerminateSpec, ComponentSpec }

class RunTimeoutSpec extends ComponentSpec with AutoTerminateSpec {
  sequential

  val component = RunTimeout

  "A RunTimeout component" should {

    "do nothing until an interval value is received" in new ComponentInstance {
      RunTimeout.outPort must receiveNothing
      this must not(terminate())
    }

    "emit a single signal and terminate after a time value is received" in new ComponentInstance {
      RunTimeout.timePort.send(500)
      within(250.millis, 750.millis) {
        RunTimeout.outPort must receive(())
      }
      this must terminate()
    }

    "ignore time values after the first" in new ComponentInstance {
      RunTimeout.timePort.send(1000)
      RunTimeout.timePort.send(200)
      within(750.millis, 1250.millis) {
        RunTimeout.outPort must receive(())
      }
      RunTimeout.outPort must receiveNothing
      this must terminate()
    }

    "terminate immediately if no interval is sent" in new ComponentInstance {
      RunTimeout.timePort.close()
      RunTimeout.outPort must receiveNothing
      this must terminate()
    }

    "not terminate before a timeout occurs, even if all in ports are closed" in new ComponentInstance {
      RunTimeout.timePort.send(3000)
      RunTimeout.timePort.close()
      this must not(terminate(2000.millis))
      this must terminate(1250.millis)
    }

    terminateItselfWhenAllOutPortsAreClosed
  }
}
