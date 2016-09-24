package net.ruippeixotog.scalafbp.runtime

import java.io.File
import java.net.URLClassLoader

import scala.reflect.runtime.universe._
import scala.util.Try

import akka.event.slf4j.SLF4JLogging
import org.clapper.classutil.ClassFinder

import net.ruippeixotog.scalafbp.component.Component

trait ComponentRegistry extends Iterable[Component] {
  def get(id: String): Option[Component]
}

class MapComponentRegistry(map: Map[String, Component]) extends ComponentRegistry {
  def get(id: String) = map.get(id)
  def iterator = map.valuesIterator
  override def size = map.size
}

class ClassFinderComponentRegistry extends ComponentRegistry with SLF4JLogging {
  private[this] val m = runtimeMirror(getClass.getClassLoader)

  private[this] val finder: ClassFinder = {
    val realClasspath = getClass.getClassLoader.asInstanceOf[URLClassLoader].
      getURLs.map { url => new File(url.getFile) }

    ClassFinder(new File(".") :: realClasspath.toList ::: ClassFinder.classpath)
  }

  private[this] def newInstance(className: String): Option[Component] = Try {
    if (className.endsWith("$")) {
      m.reflectModule(m.staticModule(className.init)).instance.asInstanceOf[Component]
    } else {
      Class.forName(className).newInstance().asInstanceOf[Component]
    }
  }.toOption

  log.info("Finding component implementations...")

  private[this] val componentMap: Map[String, Component] = {

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

    val componentClasses = findConcreteSubclasses(Set(classOf[Component].getName))
    componentClasses.flatMap(newInstance).map { comp => comp.name -> comp }.toMap
  }

  log.info(s"Found ${componentMap.size} component types")

  def get(id: String) = componentMap.get(id)
  def iterator = componentMap.valuesIterator
  override def size = componentMap.size
}

object DefaultComponentRegistry extends ClassFinderComponentRegistry
