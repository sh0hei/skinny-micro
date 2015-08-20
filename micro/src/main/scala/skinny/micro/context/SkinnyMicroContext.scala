package skinny.micro.context

import javax.servlet.ServletContext
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import skinny.micro.UnstableAccessValidation
import skinny.micro.implicits.{ CookiesImplicits, ServletApiImplicits, SessionImplicits }
import skinny.micro.request.StableHttpServletRequest

/**
 * SkinnyMicro's context for each request.
 */
trait SkinnyMicroContext
    extends ServletApiImplicits
    with SessionImplicits
    with CookiesImplicits {

  val request: HttpServletRequest

  val response: HttpServletResponse

  val servletContext: ServletContext

  val unstableAccessValidation: UnstableAccessValidation

  def surelyStable(validation: UnstableAccessValidation): SkinnyMicroContext = {
    SkinnyMicroContext.surelyStable(this, validation)
  }

}

object SkinnyMicroContext {

  private class StableSkinnyMicroContext(
      implicit val request: HttpServletRequest,
      val response: HttpServletResponse,
      val servletContext: ServletContext,
      val unstableAccessValidation: UnstableAccessValidation) extends SkinnyMicroContext {
  }

  def surelyStable(ctx: SkinnyMicroContext, validation: UnstableAccessValidation): SkinnyMicroContext = {
    new StableSkinnyMicroContext()(StableHttpServletRequest(ctx.request, validation), ctx.response, ctx.servletContext, validation)
  }

  def build(ctx: ServletContext, req: HttpServletRequest, resp: HttpServletResponse, validation: UnstableAccessValidation): SkinnyMicroContext = {
    new StableSkinnyMicroContext()(StableHttpServletRequest(req, validation), resp, ctx, validation)
  }

  def buildWithRequest(req: HttpServletRequest, validation: UnstableAccessValidation): SkinnyMicroContext = {
    new StableSkinnyMicroContext()(StableHttpServletRequest(req, validation), null, null, validation)
  }

  def buildWithoutResponse(req: HttpServletRequest, ctx: ServletContext, validation: UnstableAccessValidation): SkinnyMicroContext = {
    new StableSkinnyMicroContext()(StableHttpServletRequest(req, validation), null, ctx, validation)
  }

}
