package skinny.micro.scalate

import java.io.PrintWriter
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import org.fusesource.scalate.Binding
import skinny.micro.context.SkinnyMicroContext
import skinny.micro.routing.Route

trait ScalateUrlGeneratorSupport extends ScalateSupport {

  lazy val reflectRoutes: Map[String, Route] = {
    this.getClass.getDeclaredMethods
      .filter(_.getParameterTypes.isEmpty)
      .filter(f => classOf[Route].isAssignableFrom(f.getReturnType))
      .map(f => (f.getName, f.invoke(this).asInstanceOf[Route]))
      .toMap
  }

  override protected def createTemplateEngine(config: ConfigT) = {
    val engine = super.createTemplateEngine(config)
    val routeBindings = this.reflectRoutes.keys map (Binding(_, classOf[Route].getName))
    engine.bindings = engine.bindings ::: routeBindings.toList
    engine
  }

  override protected def createRenderContext(
    req: HttpServletRequest,
    res: HttpServletResponse,
    out: PrintWriter)(implicit ctx: SkinnyMicroContext) = {
    val context = super.createRenderContext(req, res, out)(ctx)
    for ((name, route) <- this.reflectRoutes) {
      context.attributes.update(name, route)
    }
    context
  }
}
