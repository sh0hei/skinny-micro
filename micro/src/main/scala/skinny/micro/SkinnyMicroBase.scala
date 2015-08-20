package skinny.micro

import scala.language.implicitConversions
import scala.language.reflectiveCalls

import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

import java.io.{ File, FileInputStream }
import java.util.concurrent.atomic.AtomicBoolean
import javax.servlet.http.{ HttpServletResponse, HttpServlet, HttpServletRequest }
import javax.servlet._

import skinny.micro.async.{ AsyncSupported, AsyncResult }
import skinny.micro.base._
import skinny.micro.constant._
import skinny.micro.context.SkinnyMicroContext
import skinny.micro.control.{ HaltPassControl, PassException, HaltException }
import skinny.micro.implicits._
import skinny.micro.multipart.FileCharset
import skinny.micro.response.{ ResponseStatus, ActionResult, Found }
import skinny.micro.routing._
import skinny.micro.util.UriDecoder
import skinny.util.LoanPattern._

/**
 * The base implementation of the SkinnyMicro DSL.
 * Intended to be portable to all supported backends.
 */
trait SkinnyMicroBase
    extends CoreHandler
    with AsyncSupported // can mix async and thread-based model
    with UnstableAccessValidationConfig
    with RouteRegistryAccessor
    with ErrorHandlerAccessor
    with ServletContextAccessor
    with EnvironmentAccessor
    with ParamsAccessor
    with RequestFormatAccessor
    with ResponseContentTypeAccessor
    with ResponseStatusAccessor
    with UrlGenerator
    with HaltPassControl
    with ServletApiImplicits
    with RouteMatcherImplicits
    with CookiesImplicits
    with SkinnyMicroParamsImplicits
    with DefaultImplicits
    with RicherStringImplicits
    with SessionImplicits {

  import SkinnyMicroBase._

  /**
   * ExecutionContext implicit value for this web controller.
   */
  implicit protected def executionContext: ExecutionContext = ExecutionContext.global

  /**
   * true if async supported
   */
  protected def isAsyncExecutable(result: Any): Boolean = false

  /**
   * Returns rout base path.
   */
  protected def routeBasePath(implicit ctx: SkinnyMicroContext): String

  /**
   * Default charset.
   */
  lazy val charset: Option[String] = Some("utf-8")

  /**
   * Executes routes in the context of the current request and response.
   *
   * $ 1. Executes each before filter with `runFilters`.
   * $ 2. Executes the routes in the route registry with `runRoutes` for
   * the request's method.
   * a. The result of runRoutes becomes the _action result_.
   * b. If no route matches the requested method, but matches are
   * found for other methods, then the `doMethodNotAllowed` hook is
   * run with each matching method.
   * c. If no route matches any method, then the `doNotFound` hook is
   * run, and its return value becomes the action result.
   * $ 3. If an exception is thrown during the before filters or the route
   * $    actions, then it is passed to the `errorHandler` function, and its
   * $    result becomes the action result.
   * $ 4. Executes the after filters with `runFilters`.
   * $ 5. The action result is passed to `renderResponse`.
   */
  protected def executeRoutes(request: HttpServletRequest, response: HttpServletResponse) {
    var result: Any = null
    var rendered = true

    def runActions(request: HttpServletRequest, response: HttpServletResponse) = {
      val prehandleException = request.get(SkinnyMicroBase.PrehandleExceptionKey)
      if (prehandleException.isEmpty) {
        SkinnyMicroBase.onCompleted { _ =>
          val className = this.getClass.toString
          this match {
            case f: Filter if !request.contains(s"skinny.micro.SkinnyMicroFilter.afterFilters.Run (${className})") =>
              request(s"skinny.micro.SkinnyMicroFilter.afterFilters.Run (${className})") = new {}
              runFilters(routes.afterFilters)
            case f: HttpServlet if !request.contains("skinny.micro.SkinnyMicroServlet.afterFilters.Run") =>
              request("skinny.micro.SkinnyMicroServlet.afterFilters.Run") = new {}
              runFilters(routes.afterFilters)
            case _ =>
          }
        }(context)
        runFilters(routes.beforeFilters)
        val actionResult = runRoutes(routes(request.requestMethod)).headOption
        // Give the status code handler a chance to override the actionResult
        val r = handleStatusCode(status) getOrElse {
          actionResult orElse matchOtherMethods() getOrElse doNotFound()
        }
        rendered = false
        r
      } else {
        throw prehandleException.get.asInstanceOf[Exception]
      }
    }

    cradleHalt(
      body = {
        result = runActions(request, response)
      },
      errorHandler = { error =>
        cradleHalt(
          body = {
            result = currentErrorHandler.apply(error)
            rendered = false
          },
          errorHandler =
            e => {
              SkinnyMicroBase.runCallbacks(Failure(e))(skinnyMicroContext)
              try {
                renderUncaughtException(e)(skinnyMicroContext)
              } finally {
                SkinnyMicroBase.runRenderCallbacks(Failure(e))(context)
              }
            }
        )
      }
    )
    if (!rendered) renderResponse(result)(context)
  }

  private[this] def cradleHalt(body: => Any, errorHandler: Throwable => Any): Any = {
    try {
      body
    } catch {
      case e: HaltException =>
        try {
          handleStatusCode(extractStatusCode(e)) match {
            case Some(result) => renderResponse(result)(context)
            case _ => renderHaltException(e)(context)
          }
        } catch {
          case e: HaltException => renderHaltException(e)(context)
          case scala.util.control.NonFatal(e) => errorHandler.apply(e)
          case e: Throwable => {
            errorHandler.apply(e)
            throw e
          }
        }
      case scala.util.control.NonFatal(e) => errorHandler.apply(e)
      case e: Throwable => {
        errorHandler.apply(e)
        throw e
      }
    }
  }

  protected def renderUncaughtException(e: Throwable)(implicit ctx: SkinnyMicroContext): Unit = {
    (status = 500)(ctx)
    if (isDevelopmentMode) {
      (contentType = "text/plain")(ctx)
      e.printStackTrace(ctx.response.getWriter)
    }
  }

  /**
   * Invokes each filters with `invoke`.
   * The results of the filters are discarded.
   */
  private[this] def runFilters(filters: Traversable[Route]): Unit = {
    for {
      route <- filters
      matchedRoute <- route(requestPath(context))
    } invoke(matchedRoute)
  }

  /**
   * Lazily invokes routes with `invoke`.
   * The results of the routes are returned as a stream.
   */
  protected def runRoutes(routes: Traversable[Route]): Stream[Any] = {
    for {
      route <- routes.toStream // toStream makes it lazy so we stop after match
      matchedRoute <- route.apply(requestPath(context))
      saved = saveMatchedRoute(matchedRoute)
      actionResult <- invoke(saved)
    } yield actionResult
  }

  private[this] def saveMatchedRoute(matchedRoute: MatchedRoute): MatchedRoute = {
    request(context)("skinny.micro.MatchedRoute") = matchedRoute
    setMultiparams(Some(matchedRoute), multiParams(context))(context)
    matchedRoute
  }

  private[this] def matchedRoute(implicit ctx: SkinnyMicroContext): Option[MatchedRoute] = {
    ctx.request.get("skinny.micro.MatchedRoute").map(_.asInstanceOf[MatchedRoute])
  }

  /**
   * Invokes a route or filter.  The multiParams gathered from the route
   * matchers are merged into the existing route params, and then the action
   * is run.
   *
   * @param matchedRoute the matched route to execute
   *
   * @return the result of the matched route's action wrapped in `Some`,
   *         or `None` if the action calls `pass`.
   */
  protected def invoke(matchedRoute: MatchedRoute): Option[Any] = {
    withRouteMultiParams(Some(matchedRoute)) {
      liftAction(matchedRoute.action)
    }
  }

  private[this] def liftAction(action: Action): Option[Any] = {
    try {
      Some(action())
    } catch {
      case e: PassException => None
    }
  }

  /**
   * Called if no route matches the current request for any method.  The
   * default implementation varies between servlet and filter.
   */
  protected var doNotFound: Action

  def notFound(fun: => Any): Unit = {
    doNotFound = {
      () => fun
    }
  }

  /**
   * Called if no route matches the current request method, but routes
   * match for other methods.  By default, sends an HTTP status of 405
   * and an `Allow` header containing a comma-delimited list of the allowed
   * methods.
   */
  private[this] var doMethodNotAllowed: (Set[HttpMethod] => Any) = {
    allow =>
      status = 405
      response.headers("Allow") = allow.mkString(", ")
  }

  def methodNotAllowed(f: Set[HttpMethod] => Any): Unit = {
    doMethodNotAllowed = f
  }

  private[this] def matchOtherMethods(): Option[Any] = {
    val allow = routes.matchingMethodsExcept(request.requestMethod, requestPath(context))
    if (allow.isEmpty) None else liftAction(() => doMethodNotAllowed(allow))
  }

  private[this] def handleStatusCode(status: Int): Option[Any] = {
    for {
      handler <- routes(status)
      matchedHandler <- handler(requestPath(context))
      handlerResult <- invoke(matchedHandler)
    } yield handlerResult
  }

  protected def withRouteMultiParams[S](matchedRoute: Option[MatchedRoute])(thunk: => S): S = {
    val originalParams = multiParams(context)
    setMultiparams(matchedRoute, originalParams)(context)
    try {
      thunk
    } finally {
      request(context)(MultiParamsKey) = originalParams
    }
  }

  protected def setMultiparams[S](matchedRoute: Option[MatchedRoute], originalParams: MultiParams)(
    implicit ctx: SkinnyMicroContext): Unit = {
    val routeParams = matchedRoute.map(_.multiParams).getOrElse(Map.empty).map {
      case (key, values) =>
        key -> values.map(s => if (s.nonBlank) UriDecoder.secondStep(s) else s)
    }
    ctx.request(MultiParamsKey) = originalParams ++ routeParams
  }

  /**
   * Renders the action result to the response.
   * $ - If the content type is still null, call the contentTypeInferrer.
   * $ - Call the render pipeline on the result.
   */
  protected def renderResponse(actionResult: Any)(implicit ctx: SkinnyMicroContext): Unit = {
    actionResult match {
      case r: AsyncResult => handleFuture(r.is, r.timeout)(ctx)
      case f: Future[_] => renderResponse(AsyncResult.withFuture(f)(ctx))(ctx)
      case a =>
        if (contentType(ctx) == null) {
          contentTypeInferrer.lift(actionResult) foreach { ct =>
            (contentType = ct)(ctx)
          }
        }
        renderResponseBody(actionResult)(ctx)
    }
  }

  /**
   * A partial function to infer the content type from the action result.
   *
   * $ - "text/plain" for String
   * $ - "application/octet-stream" for a byte array
   * $ - "text/html" for any other result
   */
  protected def contentTypeInferrer: ContentTypeInferrer = {
    case s: String => "text/plain"
    case bytes: Array[Byte] => MimeTypes(bytes)
    case is: java.io.InputStream => MimeTypes(is)
    case file: File => MimeTypes(file)
    case actionResult: ActionResult =>
      actionResult.headers.find {
        case (name, value) => name equalsIgnoreCase "CONTENT-TYPE"
      }.getOrElse(("Content-Type", contentTypeInferrer(actionResult.body)))._2
    //    case Unit | _: Unit => null
    case _ => "text/html"
  }

  /**
   * Renders the action result to the response body via the render pipeline.
   *
   * @see #renderPipeline
   */
  protected def renderResponseBody(actionResult: Any)(implicit ctx: SkinnyMicroContext): Unit = {
    @tailrec def loop(ar: Any): Any = ar match {
      case _: Unit | Unit => runRenderCallbacks(Success(actionResult))(ctx)
      case a => loop(renderPipeline(ctx).lift(a).getOrElse(()))
    }
    def handle(e: Throwable) = {
      runCallbacks(Failure(e))(ctx)
      try { renderUncaughtException(e)(ctx) }
      finally { runRenderCallbacks(Failure(e))(ctx) }
    }
    try {
      runCallbacks(Success(actionResult))(ctx)
      loop(actionResult)
    } catch {
      case scala.util.control.NonFatal(e) => handle(e)
      case e: Throwable => {
        handle(e)
        throw e
      }
    }
  }

  /**
   * The render pipeline is a partial function of Any => Any.  It is
   * called recursively until it returns ().  () indicates that the
   * response has been rendered.
   */
  protected def renderPipeline(implicit ctx: SkinnyMicroContext): RenderPipeline = {
    case 404 =>
      doNotFound()
    case ActionResult(status, x: Int, resultHeaders) =>
      ctx.response.status = status
      resultHeaders foreach {
        case (name, value) => ctx.response.addHeader(name, value)
      }
      ctx.response.writer.print(x.toString)
    case status: Int =>
      ctx.response.status = ResponseStatus(status)
    case bytes: Array[Byte] =>
      if (contentType(ctx) != null && contentType(ctx).startsWith("text")) {
        ctx.response.setCharacterEncoding(FileCharset(bytes).name)
      }
      ctx.response.outputStream.write(bytes)
    case is: java.io.InputStream =>
      using(is) {
        util.io.copy(_, ctx.response.outputStream)
      }
    case file: File =>
      if (contentType(ctx).startsWith("text")) {
        ctx.response.setCharacterEncoding(FileCharset(file).name)
      }
      using(new FileInputStream(file)) {
        in => util.io.zeroCopy(in, ctx.response.outputStream)
      }
    // If an action returns Unit, it assumes responsibility for the response
    case _: Unit | Unit | null =>
    // If an action returns Unit, it assumes responsibility for the response
    case ActionResult(ResponseStatus(404, _), _: Unit | Unit, _) => doNotFound()
    case actionResult: ActionResult =>
      ctx.response.status = actionResult.status
      actionResult.headers.foreach {
        case (name, value) => ctx.response.addHeader(name, value)
      }
      actionResult.body
    case x =>
      ctx.response.writer.print(x.toString)
  }

  protected def renderHaltException(e: HaltException)(implicit ctx: SkinnyMicroContext): Unit = {
    try {
      var rendered = false
      e match {
        case HaltException(Some(404), _, _, _: Unit | Unit) |
          HaltException(_, _, _, ActionResult(ResponseStatus(404, _), _: Unit | Unit, _)) =>
          renderResponse(doNotFound())(ctx)
          rendered = true
        case HaltException(Some(status), Some(reason), _, _) =>
          ctx.response.status = ResponseStatus(status, reason)
        case HaltException(Some(status), None, _, _) =>
          ctx.response.status = ResponseStatus(status)
        case HaltException(None, _, _, _) => // leave status line alone
      }
      e.headers foreach {
        case (name, value) => ctx.response.addHeader(name, value)
      }
      if (!rendered) renderResponse(e.body)(ctx)
    } catch {
      case scala.util.control.NonFatal(e) =>
        runCallbacks(Failure(e))(ctx)
        renderUncaughtException(e)(skinnyMicroContext)
        runCallbacks(Failure(e))(ctx)
      case e: Throwable =>
        runCallbacks(Failure(e))(ctx)
        renderUncaughtException(e)(skinnyMicroContext)
        runCallbacks(Failure(e))(ctx)
        throw e
    }
  }

  protected def extractStatusCode(e: HaltException): Int = e match {
    case HaltException(Some(status), _, _, _) => status
    case _ => response.status.code
  }

  /**
   * Removes _all_ the actions of a given route for a given HTTP method.
   * If addRoute is overridden then this should probably be overriden too.
   *
   * @see skinny.micro.SkinnyMicroKernel#addRoute
   */
  protected def removeRoute(method: HttpMethod, route: Route): Unit = {
    routes.removeRoute(method, route)
  }

  protected def removeRoute(method: String, route: Route): Unit = {
    removeRoute(HttpMethod(method), route)
  }

  private[this] def addStatusRoute(codes: Range, action: => Any): Unit = {
    val route = Route(Seq.empty, () => action, (req: HttpServletRequest) => routeBasePath(skinnyMicroContext))
    routes.addStatusRoute(codes, route)
  }

  /**
   * Sends a redirect response and immediately halts the current action.
   */
  def redirect(uri: String)(implicit ctx: SkinnyMicroContext): Nothing = {
    halt(Found(fullUrl(uri, includeServletPath = false)(ctx)))
  }

  /**
   * The effective path against which routes are matched.  The definition
   * varies between servlets and filters.
   */
  def requestPath(implicit ctx: SkinnyMicroContext): String

  private[this] def onAsyncEvent(event: AsyncEvent)(thunk: => Any): Unit = {
    withRequest(event.getSuppliedRequest.asInstanceOf[HttpServletRequest]) {
      withResponse(event.getSuppliedResponse.asInstanceOf[HttpServletResponse]) {
        thunk
      }
    }
  }

  private[this] def withinAsyncContext(context: javax.servlet.AsyncContext)(thunk: => Any): Unit = {
    withRequest(context.getRequest.asInstanceOf[HttpServletRequest]) {
      withResponse(context.getResponse.asInstanceOf[HttpServletResponse]) {
        thunk
      }
    }
  }

  private[this] def handleFuture(f: Future[_], timeout: Duration)(implicit ctx: SkinnyMicroContext): Unit = {
    val gotResponseAlready = new AtomicBoolean(false)
    val context: AsyncContext = ctx.request.startAsync(ctx.request, ctx.response)
    if (timeout.isFinite()) context.setTimeout(timeout.toMillis) else context.setTimeout(-1)

    def renderFutureResult(f: Future[_]): Unit = {
      f.onComplete {
        // Loop until we have a non-future result
        case Success(f2: Future[_]) => renderFutureResult(f2)
        case Success(r: AsyncResult) => renderFutureResult(r.is)
        case t => {
          if (gotResponseAlready.compareAndSet(false, true)) {
            withinAsyncContext(context) {
              try {
                t map { result =>
                  renderResponse(result)(ctx)
                } recover {
                  case e: HaltException =>
                    renderHaltException(e)(ctx)
                  case e =>
                    try {
                      renderResponse(currentErrorHandler.apply(e))(ctx)
                    } catch {
                      case scala.util.control.NonFatal(e) =>
                        SkinnyMicroBase.runCallbacks(Failure(e))(ctx)
                        renderUncaughtException(e)(ctx)
                        SkinnyMicroBase.runRenderCallbacks(Failure(e))(ctx)
                      case e: Throwable =>
                        SkinnyMicroBase.runCallbacks(Failure(e))(ctx)
                        renderUncaughtException(e)(ctx)
                        SkinnyMicroBase.runRenderCallbacks(Failure(e))(ctx)
                        throw e
                    }
                }
              } finally {
                context.complete()
              }
            }
          }
        }
      }
    }

    context.addListener(new AsyncListener {
      def onTimeout(event: AsyncEvent): Unit = {
        onAsyncEvent(event) {
          if (gotResponseAlready.compareAndSet(false, true)) {
            renderHaltException(HaltException(Some(504), None, Map.empty, "Gateway timeout"))(ctx)
            event.getAsyncContext.complete()
          }
        }
      }
      def onComplete(event: AsyncEvent): Unit = {}
      def onError(event: AsyncEvent): Unit = {
        onAsyncEvent(event) {
          if (gotResponseAlready.compareAndSet(false, true)) {
            event.getThrowable match {
              case e: HaltException => renderHaltException(e)(ctx)
              case e =>
                try {
                  renderResponse(currentErrorHandler.apply(e))(ctx)
                } catch {
                  case scala.util.control.NonFatal(e) =>
                    SkinnyMicroBase.runCallbacks(Failure(e))(ctx)
                    renderUncaughtException(e)(ctx)
                    SkinnyMicroBase.runRenderCallbacks(Failure(e))(ctx)
                  case e: Throwable =>
                    SkinnyMicroBase.runCallbacks(Failure(e))(ctx)
                    renderUncaughtException(e)(ctx)
                    SkinnyMicroBase.runRenderCallbacks(Failure(e))(ctx)
                    throw e
                }
            }
          }
        }
      }
      def onStartAsync(event: AsyncEvent): Unit = {}
    })
    renderFutureResult(f)
  }

}

object SkinnyMicroBase {

  import ServletApiImplicits._
  import scala.collection.JavaConverters._

  /**
   * A key for request attribute that contains any exception
   * that might have occured before the handling has been
   * propagated to SkinnyMicroBase#handle (such as in
   * FileUploadSupport)
   */
  val PrehandleExceptionKey: String = "skinny.micro.PrehandleException"
  val HostNameKey: String = "skinny.micro.HostName"
  val PortKey: String = "skinny.micro.Port"
  val ForceHttpsKey: String = "skinny.micro.ForceHttps"

  private[this] val KeyPrefix: String = getClass.getName

  val Callbacks: String = s"$KeyPrefix.callbacks"
  val RenderCallbacks: String = s"$KeyPrefix.renderCallbacks"
  val IsAsyncKey: String = s"$KeyPrefix.isAsync"

  def isAsyncResponse(implicit ctx: SkinnyMicroContext): Boolean = ctx.request.get(IsAsyncKey).exists(_ => true)

  def onSuccess(fn: Any => Unit)(implicit ctx: SkinnyMicroContext): Unit = addCallback(_.foreach(fn))

  def onFailure(fn: Throwable => Unit)(implicit ctx: SkinnyMicroContext): Unit = addCallback(_.failed.foreach(fn))

  def onCompleted(fn: Try[Any] => Unit)(implicit ctx: SkinnyMicroContext): Unit = addCallback(fn)

  def onRenderedSuccess(fn: Any => Unit)(implicit ctx: SkinnyMicroContext): Unit = addRenderCallback(_.foreach(fn))

  def onRenderedFailure(fn: Throwable => Unit)(implicit ctx: SkinnyMicroContext): Unit = addRenderCallback(_.failed.foreach(fn))

  def onRenderedCompleted(fn: Try[Any] => Unit)(implicit ctx: SkinnyMicroContext): Unit = addRenderCallback(fn)

  def callbacks(implicit ctx: SkinnyMicroContext): List[(Try[Any]) => Unit] =
    ctx.request.getOrElse(Callbacks, List.empty[Try[Any] => Unit]).asInstanceOf[List[Try[Any] => Unit]]

  def addCallback(callback: Try[Any] => Unit)(implicit ctx: SkinnyMicroContext): Unit = {
    ctx.request(Callbacks) = callback :: callbacks
  }

  def runCallbacks(data: Try[Any])(implicit ctx: SkinnyMicroContext): Unit = {
    callbacks.reverse foreach (_(data))
  }

  def renderCallbacks(implicit ctx: SkinnyMicroContext): List[(Try[Any]) => Unit] = {
    ctx.request.getOrElse(RenderCallbacks, List.empty[Try[Any] => Unit]).asInstanceOf[List[Try[Any] => Unit]]
  }

  def addRenderCallback(callback: Try[Any] => Unit)(implicit ctx: SkinnyMicroContext): Unit = {
    ctx.request(RenderCallbacks) = callback :: renderCallbacks
  }

  def runRenderCallbacks(data: Try[Any])(implicit ctx: SkinnyMicroContext): Unit = {
    renderCallbacks.reverse foreach (_(data))
  }

  def getServletRegistration(app: SkinnyMicroBase): Option[ServletRegistration] = {
    val registrations = app.servletContext.getServletRegistrations.values().asScala.toList
    registrations.find(_.getClassName == app.getClass.getName)
  }

}
