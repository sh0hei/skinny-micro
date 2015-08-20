package skinny.micro.base

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import skinny.micro.{ UnstableAccessValidation, Handler, SkinnyMicroBase }
import skinny.micro.context.SkinnyMicroContext
import skinny.micro.flash.FlashMap
import skinny.micro.implicits.{ ServletApiImplicits, SessionImplicits }

/**
 * Allows an action to set key-value pairs in a transient state that is accessible only to the next action and is expired immediately after that.
 * This is especially useful when using the POST-REDIRECT-GET pattern to trace the result of an operation.
 * {{{
 * post("/article/create") {
 *   // create session
 *   flash("notice") = "article created succesfully"
 *   redirect("/home")
 * }
 * get("/home") {
 *   // this will access the value set in previous action
 *   stuff_with(flash("notice"))
 * }
 * }}}
 * @see FlashMap
 */
trait FlashMapSupport
    extends Handler
    with ServletContextAccessor
    with SkinnyMicroContextInitializer
    with UnstableAccessValidationConfig
    with ServletApiImplicits
    with SessionImplicits {

  import FlashMapSupport._

  abstract override def handle(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    withRequest(req) {
      val context = SkinnyMicroContext.build(servletContext, req, res, UnstableAccessValidation(unstableAccessValidationEnabled))
      val f = flash(context)
      val isOutermost = !req.contains(LockKey)

      SkinnyMicroBase.onCompleted { _ =>
        /*
         * http://github.com/scalatra/scalatra/issues/41
         * http://github.com/scalatra/scalatra/issues/57
         *
         * Only the outermost FlashMapSupport sweeps it at the end.
         * This deals with both nested filters and redirects to other servlets.
         */
        if (isOutermost) {
          f.sweep()
        }
        flashMapSetSession(f)(context)
      }(context)

      if (isOutermost) {
        req(LockKey) = "locked"
        if (sweepUnusedFlashEntries(req)) {
          f.flag()
        }
      }

      super.handle(req, res)
    }
  }

  /**
   * Override to implement custom session retriever, or sanity checks if session is still active
   * @param f
   */
  def flashMapSetSession(f: FlashMap)(implicit ctx: SkinnyMicroContext): Unit = {
    try {
      // Save flashMap to Session after (a session could stop existing during a request, so catch exception)
      session(ctx)(SessionKey) = f
    } catch {
      case scala.util.control.NonFatal(_) =>
    }
  }

  private[this] def getFlash(implicit ctx: SkinnyMicroContext): FlashMap =
    ctx.request.get(SessionKey).map(_.asInstanceOf[FlashMap]).getOrElse {
      val map = session(ctx).get(SessionKey).map {
        _.asInstanceOf[FlashMap]
      }.getOrElse(new FlashMap)

      ctx.request.setAttribute(SessionKey, map)
      map
    }

  /**
   * Returns the [[FlashMap]] instance for the current request.
   */
  def flash(implicit ctx: SkinnyMicroContext): FlashMap = getFlash(ctx)

  def flash(key: String)(implicit ctx: SkinnyMicroContext): Any = getFlash(ctx)(key)

  /**
   * Determines whether unused flash entries should be swept.  The default is false.
   */
  protected def sweepUnusedFlashEntries(req: HttpServletRequest): Boolean = false

}

object FlashMapSupport {

  val SessionKey = FlashMapSupport.getClass.getName + ".flashMap"

  val LockKey = FlashMapSupport.getClass.getName + ".lock"

  val FlashMapKey = "skinny.micro.FlashMap"

}
