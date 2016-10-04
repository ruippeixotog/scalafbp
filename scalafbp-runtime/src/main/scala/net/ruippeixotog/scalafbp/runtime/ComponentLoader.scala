package net.ruippeixotog.scalafbp.runtime

import scala.reflect.runtime.universe._
import scala.util.Try

import akka.event.slf4j.SLF4JLogging
import org.clapper.classutil.ClassFinder

import net.ruippeixotog.scalafbp.component.Component

object ComponentLoader extends SLF4JLogging {

  def allInClasspath: Iterable[Component] = {
    val m = runtimeMirror(getClass.getClassLoader)
    val finder = ClassFinder()

    def newInstance(className: String): Option[Component] = Try {
      if (className.endsWith("$")) {
        m.reflectModule(m.staticModule(className.init)).instance.asInstanceOf[Component]
      } else {
        Class.forName(className).newInstance().asInstanceOf[Component]
      }
    }.toOption

    def findConcreteSubclasses(supers: Set[String], found: Set[String] = Set.empty): Set[String] = {
      if (supers.isEmpty) found
      else {
        val candidates = finder.getClasses.filter { info =>
          supers.contains(info.superClassName) || info.interfaces.exists(supers.contains)
        }
        val newFound = candidates.filter { info => info.isConcrete && info.isPublic }.map(_.name)
        val newSupers = candidates.filterNot(_.isFinal).map(_.name)

        findConcreteSubclasses(newSupers.toSet, found ++ newFound)
      }
    }

    log.info("Finding component implementations...")
    val components = findConcreteSubclasses(Set(classOf[Component].getName)).flatMap(newInstance)
    log.info(s"Found ${components.size} component types")
    components
  }
}
