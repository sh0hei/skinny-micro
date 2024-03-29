package skinny.micro

import javax.servlet._
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import skinny.micro.base.MainThreadLocalEverywhere
import skinny.micro.context.SkinnyMicroContext
import skinny.micro.routing.RoutingDsl
import skinny.micro.util.UriDecoder

import scala.util.DynamicVariable

/**
 * An implementation of the SkinnyMicro DSL in a filter.  You may prefer a filter
 * to a SkinnyMicroServlet if:
 *
 * $ - you are sharing a URL space with another servlet or filter and want to
 *     delegate unmatched requests.  This is very useful when migrating
 *     legacy applications one page or resource at a time.
 *
 *
 * Unlike a SkinnyMicroServlet, does not send 404 or 405 errors on non-matching
 * routes.  Instead, it delegates to the filter chain.
 *
 * If in doubt, extend SkinnyMicroServlet instead.
 *
 * @see SkinnyMicroServlet
 */
trait SkinnyMicroFilter
    extends Filter
    with SkinnyMicroFilterBase
    with ThreadLocalFeatures {

}
