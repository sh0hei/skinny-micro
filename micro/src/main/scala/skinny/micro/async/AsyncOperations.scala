package skinny.micro.async

import javax.servlet.http.HttpServletRequest

import skinny.micro.context.SkinnyMicroContext

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future, ExecutionContext }

/**
 * Utility to build async operations.
 */
trait AsyncOperations {

  /**
   * Creates a future with implicit request.
   *
   * @param op operation inside this future
   * @param ec execution context
   * @param ctx skinny.micro context
   * @tparam A response type
   * @return response value
   */
  @deprecated("Use FutureWithContext { implicit ctx => ... } instead", since = "2.0.0")
  def futureWithRequest[A](op: (HttpServletRequest) => A)(
    implicit ec: ExecutionContext, ctx: SkinnyMicroContext): Future[A] = {
    Future { op(ctx.request) }
  }

  /**
   * Creates a future with implicit context.
   *
   * @param op operation inside this future
   * @param ec execution context
   * @param context context
   * @tparam A response type
   * @return response value
   */
  def FutureWithContext[A](op: (SkinnyMicroContext) => A)(
    implicit ec: ExecutionContext, context: SkinnyMicroContext): Future[A] = {
    Future { op(context) }
  }

  /**
   * Awaits multiple Future's results.
   *
   * @param duration duration to await futures
   * @param fs futures
   * @param ec execution context
   * @return results
   */
  def awaitFutures[A](duration: Duration)(fs: Future[A]*)(implicit ec: ExecutionContext): Seq[A] = {
    Await.result(Future.sequence(fs), duration)
  }

}
