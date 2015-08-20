package skinny.micro.scalate

import java.io.File

import org.fusesource.scalate.TemplateEngine

class SkinnyMicroTemplateEngine(
  sourceDirectories: Traversable[File] = None,
  mode: String = sys.props.getOrElse("scalate.mode", "production"))
    extends TemplateEngine(sourceDirectories, mode)
