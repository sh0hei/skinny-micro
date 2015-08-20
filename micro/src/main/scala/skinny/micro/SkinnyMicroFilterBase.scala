package skinny.micro

import javax.servlet.http.{ HttpServletResponse, HttpServletRequest }
import javax.servlet.{ FilterConfig, ServletResponse, ServletRequest, FilterChain }

import skinny.micro.context.SkinnyMicroContext
import skinny.micro.util.UriDecoder

import scala.util.DynamicVariable

/**
 * Base trait for SkinnyMicroFilter implementations.
 */
trait SkinnyMicroFilterBase extends SkinnyMicroBase {

  private[this] val _filterChain: DynamicVariable[FilterChain] = new DynamicVariable[FilterChain](null)

  protected def filterChain: FilterChain = _filterChain.value

  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    val httpRequest = request.asInstanceOf[HttpServletRequest]
    val httpResponse = response.asInstanceOf[HttpServletResponse]

    _filterChain.withValue(chain) {
      handle(httpRequest, httpResponse)
    }
  }

  // What goes in servletPath and what goes in pathInfo depends on how the underlying servlet is mapped.
  // Unlike the SkinnyMicro servlet, we'll use both here by default.  Don't like it?  Override it.
  override def requestPath(implicit ctx: SkinnyMicroContext): String = {
    val request = ctx.request
    def getRequestPath: String = request.getRequestURI match {
      case requestURI: String =>
        var uri = requestURI
        if (request.getContextPath.length > 0) uri = uri.substring(request.getContextPath.length)
        if (uri.length == 0) {
          uri = "/"
        } else {
          val pos = uri.indexOf(';')
          if (pos >= 0) uri = uri.substring(0, pos)
        }
        UriDecoder.firstStep(uri)
      case null => "/"
    }

    request.get("skinny.micro.SkinnyMicroFilter.requestPath") match {
      case Some(uri) => uri.toString
      case _ => {
        val requestPath = getRequestPath
        request.setAttribute("skinny.micro.SkinnyMicroFilter.requestPath", requestPath)
        requestPath.toString
      }
    }
  }

  override protected def routeBasePath(implicit ctx: SkinnyMicroContext): String = {
    if (ctx.servletContext == null) {
      throw new IllegalStateException("routeBasePath requires an initialized servlet context to determine the context path")
    }
    ctx.servletContext.getContextPath
  }

  protected var doNotFound: Action = () => filterChain.doFilter(request, response)

  methodNotAllowed { _ => filterChain.doFilter(request, response) }

  type ConfigT = FilterConfig

  // see Initializable.initialize for why
  def init(filterConfig: FilterConfig): Unit = {
    initialize(filterConfig)
  }

  def destroy(): Unit = {
    shutdown()
  }

}
