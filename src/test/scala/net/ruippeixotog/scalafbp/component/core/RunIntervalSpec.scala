package net.ruippeixotog.scalafbp.component.core

import scala.concurrent.duration._
import spray.json._

import net.ruippeixotog.scalafbp.component.ComponentSpec

class RunIntervalSpec extends ComponentSpec {
  sequential

  val component = RunInterval

  "A Repeat component" should {

    "do nothing until an interval value is received" in new ComponentInstance {
      RunInterval.outPort must receiveNothing
      this must not(terminate())
    }

    "emit a signal periodically after an interval value is received" in new ComponentInstance {
      RunInterval.intervalPort.send(500)
      foreach(1 to 3) { _ =>
        within(250.millis, 750.millis) {
          RunInterval.outPort must receive(())
        }
      }
    }

    "update the periodicity if a new interval value is received" in new ComponentInstance {
      RunInterval.intervalPort.send(1000)
      within(750.millis, 1250.millis) {
        RunInterval.outPort must receive(())
      }
      RunInterval.intervalPort.send(500)
      within(250.millis, 1250.millis) {
        RunInterval.outPort must receive(())
      }
      foreach(1 to 3) { _ =>
        within(250.millis, 750.millis) {
          RunInterval.outPort must receive(())
        }
      }
    }

    "stop the component if a signal is received in the stop port" in new ComponentInstance {
      RunInterval.intervalPort.send(500)
      within(250.millis, 750.millis) {
        RunInterval.outPort must receive(())
      }
      RunInterval.stopPort.send(())
      RunInterval.outPort must receiveNothing.eventually(2, 0.millis)
      this must terminate()
    }

    "terminate immediately if no interval is sent" in new ComponentInstance {
      RunInterval.intervalPort.close()
      this must terminate()
    }

    "not terminate even if all in ports are closed as long as an interval was set" in new ComponentInstance {
      RunInterval.intervalPort.send(500)
      RunInterval.intervalPort.close()
      RunInterval.stopPort.close()
      this must not(terminate())
    }
  }
}
