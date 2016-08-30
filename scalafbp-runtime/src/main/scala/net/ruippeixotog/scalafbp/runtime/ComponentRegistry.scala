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

  def newInstance(className: String): Option[Component] = Try {
    if (className.endsWith("$")) {
      m.reflectModule(m.staticModule(className.init)).instance.asInstanceOf[Component]
    } else {
      Class.forName(className).newInstance().asInstanceOf[Component]
    }
  }.toOption

  log.info("Finding component implementations...")

  private[this] val componentMap: Map[String, Component] = {
    val subclassInfo = ClassFinder.concreteSubclasses(classOf[Component], finder.getClasses)
    subclassInfo.flatMap { cinfo => newInstance(cinfo.name) }.map { comp => comp.name -> comp }.toMap
  }

  log.info(s"Found ${componentMap.size} component types")

  def get(id: String) = componentMap.get(id)
  def iterator = componentMap.valuesIterator
  override def size = componentMap.size
}

object DefaultComponentRegistry extends ClassFinderComponentRegistry
