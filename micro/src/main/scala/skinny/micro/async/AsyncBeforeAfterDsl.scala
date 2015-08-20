package skinny.micro.async

import skinny.micro.{ Context, RouteTransformer }
import skinny.micro.base.{ SkinnyMicroContextInitializer, RouteRegistryAccessor }
import skinny.micro.routing.Route

/**
 * Before/After DSL for Async web apps.
 */
trait AsyncBeforeAfterDsl { self: RouteRegistryAccessor with SkinnyMicroContextInitializer =>

  /**
   * Adds a filter to run before the route.  The filter only runs if each
   * routeMatcher returns Some.  If the routeMatchers list is empty, the
   * filter runs for all routes.
   */
  def before(transformers: RouteTransformer*)(fun: (Context) => Any): Unit = {
    routes.appendBeforeFilter(Route(transformers, () => fun(context)))
  }

  /**
   * Adds a filter to run after the route.  The filter only runs if each
   * routeMatcher returns Some.  If the routeMatchers list is empty, the
   * filter runs for all routes.
   */
  def after(transformers: RouteTransformer*)(fun: (Context) => Any): Unit = {
    routes.appendAfterFilter(Route(transformers, () => fun(context)))
  }

}
