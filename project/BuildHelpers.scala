import sbt._

object BuildHelpers {
  def projectAt(path: String) = Project(path, file(path))
}
